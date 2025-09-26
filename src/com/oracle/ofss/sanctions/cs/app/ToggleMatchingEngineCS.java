package com.oracle.ofss.sanctions.cs.app;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.*;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;

public class ToggleMatchingEngineCS {

    int currentNId = -1;

    public String toggleMatchingEngine() throws Exception {
        String currentEngine = findCurrentMatchingEngine();
        String newEsOs = toggle();

        // Load properties for cache refresh settings
        Properties props = new Properties();
        try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("Error reading properties file: " + e.getMessage());
            throw e;
        }

        // COMMENTED OUT: No longer updating FCC_CS_JRSDN_ENTITY_PIPELINE_MAP during toggle
        /*
        boolean toOS = newEsOs.equals("OS");
        String jobInd = props.getProperty(toOS ? ConstantsCS.IND_JOB_OS : ConstantsCS.IND_JOB_OT);
        String jobEnt = props.getProperty(toOS ? ConstantsCS.ENT_JOB_OS : ConstantsCS.ENT_JOB_OT);

        SQLUtilityCS.updateMatchingEngine(currentEngine, newEsOs, jobInd, jobEnt);
        */

        // NEW: Update only FCC_MR_C_MATCHINGTARGET table
        SQLUtilityCS.updateMatchingEngineTargetOnly(currentEngine, newEsOs);

        String refreshRequired = props.getProperty(ConstantsCS.REFRESH_CACHE_REQUIRED, "Y");
        if ("Y".equalsIgnoreCase(refreshRequired)) {
            refreshCache("/refreshCacheSearchengine", newEsOs);
            refreshCache("/refreshCache", newEsOs);
            Thread.sleep(3000); // wait for 3 seconds to refresh cache fully
        } else {
            System.out.println("Cache refresh skipped as per configuration (refreshCacheRequired=N)");
        }

        return newEsOs;
    }

    private void refreshCache(String endPoint, String engine) throws Exception {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("Error reading properties file: " + e.getMessage());
            throw e;
        }

        String tokenUrl = props.getProperty(ConstantsCS.TOKEN_URL);
        String clientId = props.getProperty(ConstantsCS.CLIENT_ID);
        String clientSecret = props.getProperty(ConstantsCS.CLIENT_SECRET);
        String devcorp7 = props.getProperty(ConstantsCS.DEVCORP7);
        String namespace = props.getProperty(ConstantsCS.NAMESPACE);
        String retryRequiredFlag = props.getProperty(ConstantsCS.RETRY_REQUIRED_FLAG, "Y");
        int retryMaxCount = Integer.parseInt(props.getProperty(ConstantsCS.RETRY_MAX_COUNT, "5"));

        String baseService = engine.equals("OS") ? "matching-service-os" : "matching-service-ot";

        String apiUrl = devcorp7 + "/" + namespace + "/" + baseService + endPoint;

        String bearerToken = MessageProcessingUtilityCS.getAccessToken(tokenUrl, clientId, clientSecret); // Assuming MessageProcessingUtilityCS exists

        int retryCount = 0;
        int responseCode;
        StringBuilder apiResponse = new StringBuilder();
        BufferedReader br = null;

        do {
            if (retryCount > 0) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            URL url = new URL(apiUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("ofs_remote_user", "OFS_SRV_ACCT");
            conn.setRequestProperty("accept-language", "en-US,en-U");
            conn.setRequestProperty("authorization", "Bearer " + bearerToken);
            conn.setRequestProperty("idcs_remote_user", "appuser");
            conn.setRequestProperty("locale", "en-US");
            conn.setHostnameVerifier((hostname, sslSession) -> true);

            responseCode = conn.getResponseCode();

            if (responseCode != 204) {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                String output;
                while ((output = br.readLine()) != null) {
                    apiResponse.append(output);
                }
                br.close();
            }
            conn.disconnect();

            retryCount++;
        } while ("Y".equalsIgnoreCase(retryRequiredFlag) && responseCode != 204 && retryCount <= retryMaxCount);

        if (responseCode != 204) {
            throw new Exception("Failed to refresh cache after " + retryMaxCount + " retries. Response: " + responseCode + " - " + apiResponse.toString());
        }

        System.out.println("Cache refreshed successfully for " + engine);
    }

    private String toggle() throws Exception {
        String currentEsOs = findCurrentMatchingEngine();
        // Determine the new engine (opposite of current)
        String newEsOs = "OS".equals(currentEsOs) ? "OT" : "OS";
        return newEsOs;
    }

    public String findCurrentMatchingEngine() throws Exception {
        Connection conn = null;
        try {
            conn = SQLUtilityCS.getDbConnection();
            conn.setAutoCommit(false);

            // Query the current active row (F_LRI_FLAG = 'Y')
            String queryActive = "SELECT N_ID, F_ES_OS FROM fcc_mr_c_matchingtarget WHERE F_LRI_FLAG = 'Y'";
            try (PreparedStatement pstmtQuery = conn.prepareStatement(queryActive);
                 ResultSet rs = pstmtQuery.executeQuery()) {

                if (!rs.next()) {
                    throw new SQLException("No active matching engine found (no row with F_LRI_FLAG = 'Y')");
                }

                this.currentNId = rs.getInt("N_ID");
                return rs.getString("F_ES_OS");
            }
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // Ignore rollback error
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Ignore close error
                }
            }
        }
    }
}
