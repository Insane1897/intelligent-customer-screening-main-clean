package com.oracle.ofss.sanctions.cs.app;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;
import org.json.JSONArray;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFColor;

public class MessageProcessingUtilityCS {
    public MessageProcessingUtilityCS() {
    }

    private static long labelledTime;
    private static String instanceBearerToken;
    private static final Object tokenLock = new Object();
    private static long bearerTokenRefreshInterval = 30L;
    private static String restartFlag = "N";
    private static int retryMaxCount = 5;
    private static String retryRequiredFlag = "Y";
    private static SimpleDateFormat sdf = new SimpleDateFormat(ConstantsCS.DATE_FORMAT);
    private static final AtomicInteger retryRequestNumber = new AtomicInteger(0);

    public static void screenRawMsg(String matchingEngine) throws Exception {
        long startTime = System.currentTimeMillis();

        System.out.println("=============================================================");
        System.out.println("                   MESSAGE POSTING CS STARTED                ");
        System.out.println("=============================================================");
        Properties props = new Properties();

        try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("Error reading properties file: " + e.getMessage());
            throw e;
        }
        long maxIndex = getMaxIndex(props, "msgPosting.");
        System.out.println("Inside MessageProcessingUtilityCS screenRawMsg method");
        if (maxIndex < 6) {
            System.out.println("Invalid arguments");
            System.out.println("Please send Url, filepath, tokenurl, Username and Password as arguments");
        } else {
            String tokenUrl = props.getProperty(ConstantsCS.TOKEN_URL);
            String usernm = props.getProperty(ConstantsCS.CLIENT_ID);
            String pwd = props.getProperty(ConstantsCS.CLIENT_SECRET);
            String devcorp7 = props.getProperty(ConstantsCS.DEVCORP7);
            String namespace = props.getProperty(ConstantsCS.NAMESPACE);
            String transactionService = props.getProperty(ConstantsCS.TRANSACTION_SERVICE).toLowerCase();

            String executeUrl = devcorp7 + "/" + namespace + "/csxe-real-time/executeRealTime";
            String getUrlTemplate = devcorp7 + "/" + namespace + "/csxe-real-time/RTEvents/getForRequestId/";

            System.out.println("executeUrl: " + executeUrl);
            System.out.println("getUrlTemplate: " + getUrlTemplate);

            String webServiceId = props.getProperty(ConstantsCS.WEBSERVICE_ID);

            if (maxIndex >= 10) {
                retryRequiredFlag = props.getProperty(ConstantsCS.RETRY_REQUIRED_FLAG);
                String retryMaxArg = props.getProperty(ConstantsCS.RETRY_MAX_COUNT);
                String bearerTokenRefreshArg = props.getProperty(ConstantsCS.RETRY_REFRESH_INTERVAL);
                String restartFlagArg = props.getProperty(ConstantsCS.RESTART_FLAG);
                if (!retryMaxArg.isEmpty()) {
                    retryMaxCount = Integer.parseInt(retryMaxArg);
                }
                if (!restartFlagArg.isEmpty()) {
                    restartFlag = restartFlagArg;
                }
                if (!bearerTokenRefreshArg.isEmpty()) {
                    bearerTokenRefreshInterval = Long.parseLong(bearerTokenRefreshArg);
                }
                System.out.println("UserDefinedParams:::retryRequiredFlag=" + retryRequiredFlag + "; retryMaxCount=" + retryMaxCount + "; bearerTokenRefreshInterval=" + bearerTokenRefreshInterval + "min(s); restartFlag=" + restartFlag);
            }

            try (FileInputStream fis = new FileInputStream(ConstantsCS.OUTPUT_XLSX_FILE_PATH);
                 Workbook workbook = new XSSFWorkbook(fis)) {
                Sheet sheet = workbook.getSheetAt(0);

                // Dynamically add processor columns
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) headerRow = sheet.createRow(0);
                int lastColumn = headerRow.getLastCellNum();
                if (lastColumn < 0) lastColumn = 0;

                String[] processorHeaders = {
                        matchingEngine + " REQUEST_ID",
                        matchingEngine + " CASE_ID"
                };
                int processorStartColumn = lastColumn;
                XSSFCellStyle headStyle = (XSSFCellStyle) workbook.createCellStyle();
                headStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(221, 235, 247), null));
                headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                Font font = workbook.createFont();
                font.setBold(true);
                headStyle.setFont(font);
                for (int i = 0; i < processorHeaders.length; i++) {
                    Cell headerCell = headerRow.getCell(processorStartColumn + i);
                    if (headerCell == null) headerCell = headerRow.createCell(processorStartColumn + i);
                    headerCell.setCellValue(processorHeaders[i]);
                    headerCell.setCellStyle(headStyle);
                }

                Iterator<Row> rowIterator = sheet.iterator();
                Map<String, String> seqIdToRequestMap = new LinkedHashMap<>();
                Map<String, Integer> seqIdToRowNum = new HashMap<>();
                DataFormatter formatter = new DataFormatter();
                int rowNumber = 0;

                while (rowIterator.hasNext()) {
                    Row row = (Row) rowIterator.next();
                    if (rowNumber == 0) {
                        ++rowNumber;
                        continue;
                    }
                    Cell seqCell = row.getCell(0);
                    Cell requestCell = row.getCell(2);
                    if (seqCell != null && requestCell != null) {
                        String seqId = formatter.formatCellValue(seqCell);
                        seqIdToRequestMap.put(seqId, formatter.formatCellValue(requestCell));
                        seqIdToRowNum.put(seqId, row.getRowNum());
                    }
                }

                System.out.println("[" + sdf.format(new Date()) + "] size of seqIdToRequestMap is " + seqIdToRequestMap.size());

                String candidateType = props.getProperty(ConstantsCS.CANDIDATE_TYPE, "IND");
                Map<String, String> failedRequestMap = processRequests(seqIdToRequestMap, tokenUrl, usernm, pwd, executeUrl, getUrlTemplate, sheet, seqIdToRowNum, formatter, processorStartColumn, webServiceId, headStyle, matchingEngine, candidateType);

                if (!failedRequestMap.isEmpty()) {
                    System.out.println("Job is not done yet...");
                    failedRequestMap = processRequests(failedRequestMap, tokenUrl, usernm, pwd, executeUrl, getUrlTemplate, sheet, seqIdToRowNum, formatter, processorStartColumn, webServiceId, headStyle, matchingEngine, candidateType);
                }

                // Auto-size all columns
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    sheet.autoSizeColumn(i);
                }

                FileOutputStream outFile = new FileOutputStream(ConstantsCS.OUTPUT_XLSX_FILE_PATH);
                workbook.write(outFile);
                outFile.close();

                System.out.println("[" + sdf.format(new Date()) + "] Message Processing Completed");

            } catch (Exception var36) {
                var36.printStackTrace();
                System.out.println("[" + sdf.format(new Date()) + "] Error occurred: " + var36.getMessage());
                System.exit(1);
            }

        }
        System.out.println("=============================================================");
        System.out.println("                   MESSAGE POSTING CS ENDED                  ");
        System.out.println("=============================================================");
        long endTime = System.currentTimeMillis();

        System.out.println("Time taken by Message Processor CS: " + (endTime - startTime) / 1000L + " seconds");

    }

    private static Map<String, String> processRequests(Map<String, String> seqIdToRequestMap, String tokenUrl, String usernm, String pwd, String executeUrl, String getUrlTemplate, Sheet sheet, Map<String, Integer> seqIdToRowNum, DataFormatter formatter, int processorStartColumn, String webServiceId, CellStyle headStyle, String matchingEngine, String candidateType) {
        Map<String, String> failedRequestMap = new ConcurrentHashMap<>();

        seqIdToRequestMap.entrySet().parallelStream().forEach(entry -> {
            String seqId = entry.getKey();
            String requestBody = entry.getValue();
            long startTime = System.currentTimeMillis();
            int retryCount = 0;
            int responseCode = 500;
            StringBuilder apiResponse = new StringBuilder();
            BufferedReader br = null;

String requestId = null;
String fullResponse = null;
System.out.println("[" + sdf.format(new Date()) + "] Executing REST call with SeqId: " + seqId);
do {
                if (retryCount > 0) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("[" + sdf.format(new Date()) + "] Waiting for REST call to complete...");
                }
                int currentRetry = retryRequestNumber.incrementAndGet();

                String bearerToken;
                synchronized (tokenLock) {
                    bearerToken = getAccessToken(tokenUrl, usernm, pwd);
                }
System.out.println("Access token: " + bearerToken);

try {
                    // Step 1: POST to executeRealTime
                    URL executeResturl = createURL(executeUrl + "?reqId=" + currentRetry);
                    HttpsURLConnection executeConn = (HttpsURLConnection) executeResturl.openConnection();
                    executeConn.setRequestMethod("POST");
                    executeConn.setRequestProperty("Content-Type", "application/json");
                    executeConn.setRequestProperty("ofs_remote_user", "OFS_SRV_ACCT");
                    executeConn.setRequestProperty("accept-language", "en-US,en-U");
                    executeConn.setRequestProperty("authorization", "Bearer " + bearerToken);
                    executeConn.setRequestProperty("idcs_remote_user", "appuser");
                    executeConn.setRequestProperty("locale", "en-US");
                    executeConn.setHostnameVerifier((hostname, sslSession) -> true);
                    executeConn.setDoOutput(true);
                    try (OutputStream os = executeConn.getOutputStream()) {
                        os.write(requestBody.getBytes(ConstantsCS.ENCODER));
                        os.flush();
                    }

                    responseCode = executeConn.getResponseCode();
                    if (responseCode >= 100 && responseCode <= 399) {
                        br = new BufferedReader(new InputStreamReader(executeConn.getInputStream()));
                    } else {
                        br = new BufferedReader(new InputStreamReader(executeConn.getErrorStream()));
                    }

                    apiResponse = new StringBuilder();
                    String output;
                    while ((output = br.readLine()) != null) {
                        apiResponse.append(output);
                    }
                    br.close();
                    executeConn.disconnect();

                    String postResponseStr = apiResponse.toString().trim();
                    if (!postResponseStr.isEmpty() && postResponseStr.startsWith("{")) {
                        JSONObject executeJson = new JSONObject(postResponseStr);
                        requestId = String.valueOf(executeJson.optLong("requestId"));
                    } else {
                        System.err.println("Non-JSON POST response (code " + responseCode + "): " + postResponseStr.substring(0, Math.min(200, postResponseStr.length())));
                        requestId = null;
                    }

                    // Step 2: GET to getForRequestId
                    if (requestId != null) {
                        String getUrl = getUrlTemplate + requestId;
                        URL getResturl = createURL(getUrl);
                        HttpsURLConnection getConn = (HttpsURLConnection) getResturl.openConnection();
                        getConn.setRequestMethod("GET");
                        getConn.setRequestProperty("authorization", "Bearer " + bearerToken);
                        getConn.setHostnameVerifier((hostname, sslSession) -> true);

                        responseCode = getConn.getResponseCode();
                        if (responseCode >= 100 && responseCode <= 399) {
                            br = new BufferedReader(new InputStreamReader(getConn.getInputStream()));
                        } else {
                            br = new BufferedReader(new InputStreamReader(getConn.getErrorStream()));
                        }

                        apiResponse = new StringBuilder();
                        while ((output = br.readLine()) != null) {
                            apiResponse.append(output);
                        }
                        br.close();
                        getConn.disconnect();

                        String getResponseStr = apiResponse.toString().trim();
                        fullResponse = getResponseStr;
                        if (!getResponseStr.isEmpty() && !getResponseStr.startsWith("{")) {
                            System.err.println("Non-JSON GET response (code " + responseCode + "): " + getResponseStr.substring(0, Math.min(200, getResponseStr.length())));
                        }
                    } else {
                        fullResponse = null;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    responseCode = 500; // Treat as error for retry
                } finally {
                    if (br != null) {
                        try {
                            br.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                retryCount++;
            } while ("Y".equalsIgnoreCase(retryRequiredFlag) && responseCode > 399 && retryCount <= retryMaxCount);

            System.out.println("[" + sdf.format(new Date()) + "] ResponseCode: " + responseCode);

            long endTime = System.currentTimeMillis();
            System.out.println("=============================================================----------");
            System.out.println("Time taken for rest call: " + (endTime - startTime) / 1000L + " seconds");
            System.out.println("=============================================================----------");
            System.out.println("=============================================================----------------------------------------------");

            String responseString = fullResponse != null ? fullResponse : "NA";

            String requestIdString = requestId != null ? requestId : "NA";
            long matchCount = 0;
            String status = "NA";
            String caseId = "NA";
            long filteredCount = 0;

            boolean isErrorToHandle = (responseCode == 400 || responseCode == 500 || responseCode == 503);

            if (responseCode <= 399 || isErrorToHandle) {
                String trimmed = fullResponse == null ? "" : fullResponse.trim();
                JSONObject rulesetResultsJson = new JSONObject();
                if (requestId != null) {
                    String rowCandType = "BOTH".equalsIgnoreCase(candidateType)
                                         ? detectCandidateTypeFromPayload(requestBody)
                                         : candidateType;
                    rulesetResultsJson = fetchRuleSetResults(requestId, matchingEngine, rowCandType);
                }

                Object[] excelParams = new Object[]{
                        requestIdString,
                        "NA",  // Placeholder for CASE_ID
                        responseString,
                        rulesetResultsJson.toString()
                };

                // Update sheet in synchronized block
                synchronized (sheet) {
                    int targetRowNum = seqIdToRowNum.get(seqId);
                    System.out.println("Writing output to file for seqId: " + seqId);
                    Row row = sheet.getRow(targetRowNum);
                    for (int i = 0; i < 2; i++) {  // Handle first 2 processor columns
                        Cell cell = row.getCell(processorStartColumn + i);
                        if (cell == null) cell = row.createCell(processorStartColumn + i);
                        System.out.println(excelParams[i].toString());
                        cell.setCellValue(excelParams[i].toString());
                    }

                    int currentCol = processorStartColumn + 2;
                    currentCol = writeChunkedTextToCell(sheet, row, currentCol, rulesetResultsJson.toString(), headStyle, matchingEngine + " RULESET_RESULTS");
                    currentCol = writeChunkedTextToCell(sheet, row, currentCol, responseString, headStyle, matchingEngine + " RESPONSE");
                }
            }

            if (responseCode > 399) {
                failedRequestMap.put(seqId, requestBody);
            }
            System.out.println("===========================================================================================================");
        });

        return failedRequestMap;
    }

    private static long getMaxIndex(Properties props, String prefix) throws Exception {
        long count = props.keySet().stream()
                .map(Object::toString)
                .filter(key -> key.startsWith(prefix))
                .count();
        return count;
    }

    public static String getAccessToken(String tokenUrl, String usernm, String pwd) {
        long currentTime = System.currentTimeMillis();
        long timeDiff = (currentTime - labelledTime) / 60000L;
        if (labelledTime != 0L && timeDiff < bearerTokenRefreshInterval) {
            System.out.println("--- Using cached bearerToken for " + (bearerTokenRefreshInterval - timeDiff) + " more min(s)");
            return instanceBearerToken;
        }

        String bearerToken = "";
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("grant_type", "client_credentials");
            headers.put("scope", "urn:opc:idm:__myscopes__");
            URL url1 = createURL(tokenUrl);
            HttpsURLConnection httpConn1 = (HttpsURLConnection) url1.openConnection();
            String userpass = usernm + ":" + pwd;
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
            httpConn1.setRequestMethod("POST");
            httpConn1.setDoOutput(true);
            httpConn1.setDoInput(true);
            httpConn1.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpConn1.setRequestProperty("Authorization", basicAuth);
            byte[] postDataBytes = getParamsByte(headers);
            httpConn1.getOutputStream().write(postDataBytes);
            int status = httpConn1.getResponseCode();
            if (status == 200) {
                String response = "";
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];

                int length;
                while ((length = httpConn1.getInputStream().read(buffer)) != -1) {
                    result.write(buffer, 0, length);
                }

                response = result.toString(ConstantsCS.ENCODER);
                System.out.println("AuthToken response: " + response);
                JSONObject jsonObject = new JSONObject(response);
                bearerToken = jsonObject.getString("access_token");
                httpConn1.disconnect();
            }

            System.out.println("AuthToken status: " + status);
        } catch (Exception var19) {
            var19.printStackTrace();
        }

        instanceBearerToken = bearerToken;
        labelledTime = System.currentTimeMillis();
        return bearerToken;
    }

    private static byte[] getParamsByte(Map<String, String> params) {
        byte[] result = null;
        StringBuilder postData = new StringBuilder();
        Iterator var4 = params.entrySet().iterator();

        while (var4.hasNext()) {
            Map.Entry<String, String> param = (Map.Entry) var4.next();
            if (postData.length() != 0) {
                postData.append('&');
            }

            postData.append(encodeParam(param.getKey()));
            postData.append('=');
            postData.append(encodeParam(String.valueOf(param.getValue())));
        }

        try {
            result = postData.toString().getBytes(ConstantsCS.ENCODER);
        } catch (UnsupportedEncodingException var5) {
            var5.printStackTrace();
        }

        return result;
    }

    private static String encodeParam(String data) {
        String result = "";

        try {
            result = URLEncoder.encode(data, ConstantsCS.ENCODER);
        } catch (UnsupportedEncodingException var3) {
            var3.printStackTrace();
        }

        return result;
    }

    private static Map<String, String> loadRuleSetNames(Connection conn, String pipelineName) throws SQLException {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT JSON_VALUE(C_PAYLOAD, '$.name') AS name, " +
                     "JSON_VALUE(C_PAYLOAD, '$.ruleSetId') AS ruleSetId " +
                     "FROM fcc_m_pipeline_activity " +
                     "WHERE N_PIPELINE_ID IN (SELECT N_PIPELINE_ID FROM fcc_m_pipeline " +
                     "WHERE v_pipeline_name = ? AND f_status = 'A') " +
                     "AND F_STATUS = 'A' AND N_COMPONENT_ID = 7";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, pipelineName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("ruleSetId"), rs.getString("name"));
                }
            }
        }
        return map;
    }

    private static JSONObject fetchRuleSetResults(String requestId, String matchingEngine, String candidateType) {
        JSONObject results = new JSONObject();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = SQLUtilityCS.getDbConnection();

            // Determine pipeline name based on engine and candidate type
            String pipelineKey = matchingEngine.toLowerCase() + "." + candidateType.toLowerCase() + ".pipeline";
            Properties props = new Properties();
            try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
                props.load(reader);
            }
            String pipelineName = props.getProperty(pipelineKey, "Individual Real Time Screening"); // fallback

            Map<String, String> idToName = loadRuleSetNames(conn, pipelineName);

            String query = "SELECT c_matched_result, v_ruleset_id FROM fcc_mr_matched_result_rt WHERE n_request_id = ?";
            pstmt = conn.prepareStatement(query);
            pstmt.setString(1, requestId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String jsonStr = rs.getString("c_matched_result");
                String ruleSetId = rs.getString("v_ruleset_id");
                if (jsonStr != null) {
                    JSONObject json = new JSONObject(jsonStr);
                    JSONArray matchesArray = json.optJSONArray("matches");
                    JSONObject ruleSetSummary = new JSONObject();
                    ruleSetSummary.put("ruleSetId", ruleSetId);
                    ruleSetSummary.put("matchCount", matchesArray != null ? matchesArray.length() : 0);
                    JSONArray matchesSummary = new JSONArray();
                    if (matchesArray != null) {
                        for (int i = 0; i < matchesArray.length(); i++) {
                            JSONObject match = matchesArray.getJSONObject(i);
                            JSONObject matchSum = new JSONObject();
                            matchSum.put("finalScore", match.optDouble("finalScore", 0.0));
                            matchSum.put("ruleId", match.optLong("ruleId", 0));
                            matchSum.put("indexName", match.optString("indexName", "NA"));
                            matchSum.put("n_uid", match.optString("targetAttributeKey", "NA"));
                            JSONArray matchedCols = new JSONArray();
                            JSONArray matchCols = match.optJSONArray("matchCols");
                            if (matchCols != null) {
                                for (int j = 0; j < matchCols.length(); j++) {
                                    matchedCols.put(matchCols.getJSONObject(j).optString("colName"));
                                }
                            }
                            matchSum.put("matchedCols", matchedCols);
                            matchSum.put("ruleName", match.optString("ruleName", "NA"));
                            matchesSummary.put(matchSum);
                        }
                    }
                    ruleSetSummary.put("matches", matchesSummary);
                    String ruleSetName = idToName.getOrDefault(ruleSetId, ruleSetId);
                    results.put(ruleSetName, ruleSetSummary);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (pstmt != null) pstmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return results;
    }

private static int writeChunkedTextToCell(Sheet sheet, Row row, int colIdx, String content, CellStyle headStyle, String baseHeader) {
    if (content.length() > ConstantsCS.MAX_MSG_LEN) {
        content = "[response exceeds " + ConstantsCS.MAX_MSG_LEN + " chars]";
    }
    final int MAX_CELL_LENGTH = 32000;
    Row headerRow = sheet.getRow(0);

    int chunkCount = (int) Math.ceil((double) content.length() / MAX_CELL_LENGTH);
    if (chunkCount == 0) chunkCount = 1;

    for (int chunk = 0; chunk < chunkCount; chunk++) {
        String chunkText = (chunkCount == 1) ? content : content.substring(chunk * MAX_CELL_LENGTH, Math.min((chunk + 1) * MAX_CELL_LENGTH, content.length()));

        int currentCol = colIdx + chunk;
        Cell cell = row.createCell(currentCol);
        cell.setCellValue(chunkText);

        Cell headerCell = headerRow.getCell(currentCol);
        if (headerCell == null) headerCell = headerRow.createCell(currentCol);
        String headerValue = (chunkCount == 1) ? baseHeader : baseHeader + "-" + (chunk + 1);
        if (headerCell.getStringCellValue().isEmpty()) {
            headerCell.setCellValue(headerValue);
            headerCell.setCellStyle(headStyle);
        }
    }
    return colIdx + chunkCount;
}

    private static URL createURL(String spec) throws MalformedURLException, URISyntaxException {
        return new URI(spec).toURL();
    }

    private static String detectCandidateTypeFromPayload(String requestJson) {
        if (requestJson.contains("__ORG_NAME__") ||
            requestJson.contains("\"Organization Name\"") ||
            requestJson.contains("V_ENTITY_NAME")) {
            return "ENT";
        }
        return "IND";
    }

    private static String getResponseMsg(int code) {
        String msg = "will do the needful";

        switch (code) {
            case 502:
                msg = ConstantsCS.WAIT_MSG;
                break;
            case 504:
                msg = ConstantsCS.HOLD_ON_MSG_1;
                break;
            case 503:
                msg = ConstantsCS.HOLD_ON_MSG_2;
                break;
            case 200:
                msg = ConstantsCS.SUCCESS_MSG;
                break;
            default:
                msg = ConstantsCS.LOAD_MSG;
                break;
        }
        return msg;
    }
}
