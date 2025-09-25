package com.oracle.ofss.sanctions.cs.app;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class SQLUtilityCS {
public static Map<String, List<String>> loadLookup(String displayName) throws Exception {
    Map<String, List<String>> lookupMap = new HashMap<>();
    try (Connection conn = getDbConnection()) {
        String query = "SELECT v.v_lookup_values " +
                       "FROM fcc_idx_m_lookup_values v " +
                       "JOIN fcc_idx_m_lookup l ON l.n_lookup_id = v.n_lookup_id " +
                       "WHERE l.v_lookup_display_name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, displayName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String values = rs.getString("v_lookup_values");
                    if (values != null) {
                        String[] synonyms = values.split(",");
                        for (String syn : synonyms) {
                            String key = syn.trim().toUpperCase();
                            List<String> list = lookupMap.computeIfAbsent(key, k -> new ArrayList<>());
                            for (String s : synonyms) {
                                if (!s.trim().equalsIgnoreCase(syn)) {
                                    list.add(s.trim());
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    return lookupMap;
}

public static Map<String, List<String>> loadLookupByIds(String idsCsv) throws Exception {
    Map<String, List<String>> lookupMap = new HashMap<>();
    try (Connection conn = getDbConnection()) {
        String query = "SELECT v.v_lookup_values FROM fcc_idx_m_lookup_values v WHERE v.n_lookup_id IN (" + idsCsv + ")";
        System.out.println("Executing stopword query: " + query);
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    String values = rs.getString("v_lookup_values");
                    System.out.println("Row " + rowCount + ": v_lookup_values = " + values);
                    if (values != null) {
                        String[] synonyms = values.split(",");
                        for (String syn : synonyms) {
                            String key = syn.trim().toUpperCase();
                            List<String> list = lookupMap.computeIfAbsent(key, k -> new ArrayList<>());
                            for (String s : synonyms) {
                                if (!s.trim().equalsIgnoreCase(syn)) {
                                    list.add(s.trim());
                                }
                            }
                        }
                    }
                }
                System.out.println("Total rows fetched for IDs " + idsCsv + ": " + rowCount);
            }
        }
    }
    return lookupMap;
}

public static List<String> loadFlatLookupValuesByIds(String idsCsv) throws Exception {
    List<String> lookupValues = new ArrayList<>();
    try (Connection conn = getDbConnection()) {
        String query = "SELECT v.v_lookup_values FROM fcc_idx_m_lookup_values v WHERE v.n_lookup_id IN (" + idsCsv + ")";
        System.out.println("Executing flat lookup query: " + query);
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String values = rs.getString("v_lookup_values");
                    if (values != null) {
                        lookupValues.add(values.trim());
                    }
                }
            }
        }
    }
    return lookupValues;
}

    public static Connection getDbConnection() throws Exception {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("Error reading properties file: " + e.getMessage());
            throw e;
        }

        String jdbcUrl = props.getProperty(ConstantsCS.JDBC_URL);
        String jdbcDriver = props.getProperty(ConstantsCS.JDBC_DRIVER);
        String walletname = props.getProperty(ConstantsCS.WALLET_NAME);
        String tnsAdminPath = ConstantsCS.PARENT_DIRECTORY + File.separator + ConstantsCS.BIN_FOLDER_NAME + File.separator + walletname;

        Properties properties = new Properties();
        properties.setProperty("oracle.net.tns_admin", tnsAdminPath);
        Class.forName(jdbcDriver);
        Connection connection = DriverManager.getConnection(jdbcUrl, properties);
        System.out.println("Connection established successfully!");
        return connection;
    }

    public static void updateMatchingEngine(String currentEngine, String targetEngine, String jobNameIND, String jobNameENT) throws Exception {
        try (Connection conn = getDbConnection()) {
            conn.setAutoCommit(false);

            // 1. FCC_MR_C_MATCHINGTARGET updates
            // First, set all rows for target engine to 'N'
            String updateTargetToN = "UPDATE FCC_MR_C_MATCHINGTARGET SET F_LRI_FLAG = 'N' WHERE F_ES_OS = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateTargetToN)) {
                pstmt.setString(1, targetEngine);
                pstmt.executeUpdate();
            }

            // Pick the most recent row for target engine and mark 'Y'
            String updateTargetToY = "UPDATE FCC_MR_C_MATCHINGTARGET " +
                "SET F_LRI_FLAG = 'Y', V_ACTION_BY = 'SYSTEM', D_ACTION = SYSDATE, " +
                "V_MATCHING_ENGINE = CASE WHEN ? = 'OS' THEN 'Open Search' ELSE 'Oracle Text' END " +
                "WHERE F_ES_OS = ? AND (N_ID, D_ACTION) IN (" +
                "    SELECT N_ID, D_ACTION FROM FCC_MR_C_MATCHINGTARGET " +
                "    WHERE F_ES_OS = ? ORDER BY D_ACTION DESC, N_ID DESC FETCH FIRST 1 ROWS ONLY)";
            try (PreparedStatement pstmt = conn.prepareStatement(updateTargetToY)) {
                pstmt.setString(1, targetEngine);
                pstmt.setString(2, targetEngine);
                pstmt.setString(3, targetEngine);
                pstmt.executeUpdate();
            }

            // Flip the current engine row to 'N'
            String updateCurrentToN = "UPDATE FCC_MR_C_MATCHINGTARGET " +
                "SET F_LRI_FLAG = 'N', V_ACTION_BY = 'SYSTEM', D_ACTION = SYSDATE " +
                "WHERE F_ES_OS = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateCurrentToN)) {
                pstmt.setString(1, currentEngine);
                pstmt.executeUpdate();
            }

            // 2. FCC_CS_JRSDN_ENTITY_PIPELINE_MAP updates
            // Update IND job
            String updateIND = "UPDATE FCC_CS_JRSDN_ENTITY_PIPELINE_MAP SET V_JOB_NAME = ? WHERE V_ENTITY_TYPE_CD = 'IND'";
            try (PreparedStatement pstmt = conn.prepareStatement(updateIND)) {
                pstmt.setString(1, jobNameIND);
                pstmt.executeUpdate();
            }

            // Update ENT and ORG jobs
            String updateENT = "UPDATE FCC_CS_JRSDN_ENTITY_PIPELINE_MAP SET V_JOB_NAME = ? WHERE V_ENTITY_TYPE_CD IN ('ENT','ORG')";
            try (PreparedStatement pstmt = conn.prepareStatement(updateENT)) {
                pstmt.setString(1, jobNameENT);
                pstmt.executeUpdate();
            }

            conn.commit();
            System.out.println("Matching engine toggled from " + currentEngine + " to " + targetEngine + " with DB updates completed.");
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Failed to update matching engine tables", e);
        }
    }
}
