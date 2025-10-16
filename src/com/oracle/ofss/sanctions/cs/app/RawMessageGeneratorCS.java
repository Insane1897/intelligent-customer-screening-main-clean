package com.oracle.ofss.sanctions.cs.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.*;

import com.oracle.ofss.sanctions.cs.app.TransliterationUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class RawMessageGeneratorCS {
    private static class RowData {
        private Map<String, Object> data = new HashMap<>();
        public Object get(String key) { return data.get(key.toUpperCase()); }
        public void put(String key, Object value) { data.put(key.toUpperCase(), value); }
        public Set<String> keySet() { return data.keySet(); }
    }

    private static String asString(Object o) {
        if (o == null) return "";
        if (o instanceof java.sql.Clob) {
            java.sql.Clob clob = (java.sql.Clob) o;
            try (java.io.Reader reader = clob.getCharacterStream()) {
                char[] buf = new char[8192];
                StringBuilder sb = new StringBuilder();
                int n;
                while ((n = reader.read(buf)) > 0) {
                    sb.append(buf, 0, n);
                }
                return sb.toString();
            } catch (Exception e) {
                System.err.println("Error reading CLOB: " + e.getMessage());
                return "";
            }
        }
        return o.toString();
    }

    private static Map<String, List<String>> synonymMap = new HashMap<>();
    private static List<String> stopwordList = new ArrayList<>();
    // Separate maps for BOTH mode
    private static Map<String, List<String>> indSynonymMap = new HashMap<>();
    private static List<String> indStopwordList = new ArrayList<>();
    private static Map<String, List<String>> entSynonymMap = new HashMap<>();
    private static List<String> entStopwordList = new ArrayList<>();
    // Stopword message counters for capping
    private static int indStopwordCount = 0;
    private static int entStopwordCount = 0;

    private static void loadMaps(Properties props, String candidateType, Connection connection) {
        if ("BOTH".equalsIgnoreCase(candidateType)) {
            // Load both IND and ENT maps
            if ("Y".equalsIgnoreCase(props.getProperty("enableSynonym"))) {
                try {
                    String indSynLookupIds = props.getProperty(ConstantsCS.SYN_IND_LOOKUP_IDS, "6");
                    indSynonymMap = SQLUtilityCS.loadLookupByIds(indSynLookupIds);
                    System.out.println("Loaded indSynonymMap: " + indSynonymMap.size() + " entries");

                    String entSynLookupIds = props.getProperty(ConstantsCS.SYN_ENT_LOOKUP_IDS, "11");
                    entSynonymMap = SQLUtilityCS.loadLookupByIds(entSynLookupIds);
                    System.out.println("Loaded entSynonymMap: " + entSynonymMap.size() + " entries");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if ("Y".equalsIgnoreCase(props.getProperty("enableIndStopword"))) {
                try {
                    String indStopLookupIds = props.getProperty(ConstantsCS.STOP_IND_LOOKUP_IDS, "7,8");
                    indStopwordList = SQLUtilityCS.loadFlatLookupValuesByIds(indStopLookupIds);
                    System.out.println("Loaded indStopwordList: " + indStopwordList.size() + " entries");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if ("Y".equalsIgnoreCase(props.getProperty("enableEntStopword"))) {
                try {
                    String entStopLookupIds = props.getProperty(ConstantsCS.STOP_ENT_LOOKUP_IDS, "1,4,10");
                    entStopwordList = SQLUtilityCS.loadFlatLookupValuesByIds(entStopLookupIds);
                    System.out.println("Loaded entStopwordList: " + entStopwordList.size() + " entries");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            // For IND or ENT only
            if ("Y".equalsIgnoreCase(props.getProperty("enableSynonym"))) {
                try {
                    String synLookupIds = "IND".equalsIgnoreCase(candidateType) ?
                        props.getProperty(ConstantsCS.SYN_IND_LOOKUP_IDS, "6") :
                        props.getProperty(ConstantsCS.SYN_ENT_LOOKUP_IDS, "11");
                    synonymMap = SQLUtilityCS.loadLookupByIds(synLookupIds);
                    System.out.println("Loaded synonymMap for " + candidateType + ": " + synonymMap.size() + " entries");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            String stopwordProp = "IND".equalsIgnoreCase(candidateType) ? "enableIndStopword" : "enableEntStopword";
            if ("Y".equalsIgnoreCase(props.getProperty(stopwordProp))) {
                try {
                    String stopLookupIds = "IND".equalsIgnoreCase(candidateType) ?
                        props.getProperty(ConstantsCS.STOP_IND_LOOKUP_IDS, "7,8") :
                        props.getProperty(ConstantsCS.STOP_ENT_LOOKUP_IDS, "1,4,10");
                    stopwordList = SQLUtilityCS.loadFlatLookupValuesByIds(stopLookupIds);
                    System.out.println("Loaded stopwordList for " + candidateType + ": " + stopwordList.size() + " entries");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void generateRawMessage() throws Exception {
        long startTime = System.currentTimeMillis();
        System.out.println("\n=============================================================");
        System.out.println("                RAW MESSAGE GENERATOR CS STARTED             ");
        System.out.println("=============================================================");
        Connection connection = null;
        List<Map<String, Object>> messages = new ArrayList<>();

        try {
            Properties props = new Properties();
            try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
                props.load(reader);
            } catch (IOException e) {
                System.err.println("Error reading properties file: " + e.getMessage());
                throw e;
            }

            String candidateType = props.getProperty(ConstantsCS.CANDIDATE_TYPE);
            connection = SQLUtilityCS.getDbConnection();
            String watchlistTypesStr = props.getProperty(ConstantsCS.WATCHLIST_TYPES, "");
            String[] watchlistTypes = watchlistTypesStr.split(",");
            String tagName = props.getProperty(ConstantsCS.TAGNAME);
            String webserviceId = props.getProperty(ConstantsCS.WEBSERVICE_ID);

            // Load stopword caps
            int maxIndStopwords = Integer.parseInt(props.getProperty("stopword.max.ind", "0"));
            int maxEntStopwords = Integer.parseInt(props.getProperty("stopword.max.ent", "0"));

            loadMaps(props, candidateType, connection);

            if ("BOTH".equalsIgnoreCase(candidateType)) {
                processCandidateType("IND", watchlistTypes, props, connection, messages, tagName, webserviceId, maxIndStopwords, maxEntStopwords);
                processCandidateType("ENT", watchlistTypes, props, connection, messages, tagName, webserviceId, maxIndStopwords, maxEntStopwords);
            } else {
                processCandidateType(candidateType, watchlistTypes, props, connection, messages, tagName, webserviceId, maxIndStopwords, maxEntStopwords);
            }

            System.out.println("Total raw messages generated: " + messages.size());
            writeJsonAsExcelFile(messages, props.getProperty(ConstantsCS.TRANSACTION_SERVICE), tagName, props.getProperty(ConstantsCS.WEBSERVICE), candidateType);
            writeRawMessagesToJsonFile(messages);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) connection.close();
        }

        System.out.println("\n=============================================================");
        System.out.println("                 RAW MESSAGE GENERATOR CS ENDED              ");
        System.out.println("=============================================================");
        long endTime = System.currentTimeMillis();
        System.out.println("Time taken by Raw Message Generator CS: " + (endTime - startTime) / 1000L + " seconds");
    }

    private static void processCandidateType(String type, String[] watchlistTypes, Properties props, Connection connection, List<Map<String, Object>> messages, String tagName, String webserviceId, int maxIndStopwords, int maxEntStopwords) throws Exception {
        String sourceFileName = "IND".equalsIgnoreCase(type) ? "source_ind.json" : "source_ent.json";
        String srcFilePath = ConstantsCS.PARENT_DIRECTORY + File.separator + ConstantsCS.BIN_FOLDER_NAME + File.separator + sourceFileName;
        String srcFile = loadJsonFromFile(srcFilePath, props);
        JSONObject templateJson = new JSONObject(srcFile);

        // Get dynamic token mappings for this candidate type
        Map<String, String> tokenToColumnMap = getTokenMappings(type, props);

        // Filter to only tokens present in the JSON template
        Map<String, String> filteredTokenToColumnMap = new LinkedHashMap<>();
        String templateContent = templateJson.toString();
        for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
            if (templateContent.contains(entry.getKey())) {
                filteredTokenToColumnMap.put(entry.getKey(), entry.getValue());
            }
        }

        String defaultVariationToken = "IND".equalsIgnoreCase(type) ? "__FULL_NAME__" : "__ORG_NAME__";
        String variationTokenConfig = props.getProperty("variationtoken." + type.toLowerCase(), defaultVariationToken);
        String[] variationTokenArray = variationTokenConfig.split(";");
        List<String> validVariationTokens = new ArrayList<>();
        for (String vt : variationTokenArray) {
            vt = vt.trim();
            if (filteredTokenToColumnMap.containsKey(vt)) {
                validVariationTokens.add(vt);
            } else {
                System.err.println("Variation token " + vt + " not found in filtered map for " + type + " - skipping this token");
            }
        }
        if (validVariationTokens.isEmpty()) {
            System.err.println("No valid variation tokens found for " + type + " - generating exact messages only, skipping variations");
        }
        String combineVariations = props.getProperty("combinevariations." + type.toLowerCase(), "N");

        for (String wlType : watchlistTypes) {
            wlType = wlType.trim();
            if (wlType.isEmpty()) continue;

            String tableName = ConstantsCS.TABLE_WL_MAP.get(wlType);
            if (tableName == null) continue;

            List<RowData> rows = prepareQueryAndGetTableData(connection, props, tableName, type, filteredTokenToColumnMap.values());
            List<RowData> splitRows = splitSemicolonNames(rows, type, filteredTokenToColumnMap);

            generateRawMessagesForRows(splitRows, props, templateJson, tableName, tagName, webserviceId, type, messages, wlType, filteredTokenToColumnMap, validVariationTokens, combineVariations, maxIndStopwords, maxEntStopwords);

            // Transliteration: select non-English rows and generate messages
            if ("Y".equalsIgnoreCase(props.getProperty("enableTransliteration"))) {
                List<RowData> translitRows = prepareQueryAndGetTableDataForTranslit(connection, props, tableName, type, filteredTokenToColumnMap.values());
                List<RowData> translitSplitRows = splitSemicolonNames(translitRows, type, filteredTokenToColumnMap);
                generateTranslitMessagesForRows(translitSplitRows, props, templateJson, tableName, tagName, webserviceId, type, messages, wlType, filteredTokenToColumnMap, validVariationTokens, combineVariations);
            }
        }
    }

    private static Map<String, String> getTokenMappings(String candidateType, Properties props) {
        Map<String, String> tokenToColumnMap = new LinkedHashMap<>();
        int startIndex = "IND".equalsIgnoreCase(candidateType) ? 1 : 11;  // IND starts at 1, ENT at 11
        for (int i = startIndex; ; i++) {
            String token = props.getProperty("replace.src[" + i + "]");
            String column = props.getProperty("replace.targetColumn[" + i + "]");
            if (token == null || column == null) break;
            tokenToColumnMap.put(token, column);
        }
        return tokenToColumnMap;
    }

    private static List<RowData> prepareQueryAndGetTableData(Connection connection, Properties props, String tableName, String candidateType, Collection<String> requiredColumns) throws Exception {
        List<RowData> rows = new ArrayList<>();
        String baseFilter = props.getProperty(ConstantsCS.WHERE_CLAUSE, "1=1");
        String filter = " where " + baseFilter;

        String wlFilter = "";
        if ("IND".equalsIgnoreCase(candidateType)) {
            wlFilter = " V_ENTITY_TYPE = 'I'";
        } else if ("ENT".equalsIgnoreCase(candidateType)) {
            wlFilter = " V_ENTITY_TYPE != 'I'";
        }
        if (!wlFilter.isEmpty()) {
            filter += " AND " + wlFilter;
        }

        // Build SELECT clause with required columns
        Set<String> columns = new LinkedHashSet<>(requiredColumns);
        columns.add("N_UID"); // Always include UID
        String selectClause = String.join(", ", columns);

        String query = "select " + selectClause + " from " + tableName + filter;
        System.out.println("SQL Query generated:: " + query);

        try (PreparedStatement pst = connection.prepareStatement(query);
             ResultSet rs = pst.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            while (rs.next()) {
                RowData row = new RowData();
                for (int i = 1; i <= colCount; i++) {
                    String colName = md.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(colName, value);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<RowData> prepareQueryAndGetTableData(Connection connection, Properties props, String tableName, String candidateType) throws Exception {
        return prepareQueryAndGetTableData(connection, props, tableName, candidateType, Arrays.asList("V_FULL_NAME", "V_ENTITY_NAME"));
    }

    private static List<RowData> prepareQueryAndGetTableDataForTranslit(Connection connection, Properties props, String tableName, String candidateType, Collection<String> requiredColumns) throws Exception {
        List<RowData> rows = new ArrayList<>();
        String filter = " where 1=1";

        String wlFilter = "";
        if ("IND".equalsIgnoreCase(candidateType)) {
            wlFilter = " V_ENTITY_TYPE = 'I' AND REGEXP_LIKE(V_FULL_NAME, '[^\\x00-\\x7F]') AND LENGTH(V_FULL_NAME) < 50 AND REGEXP_LIKE(V_FAMILY_NAME, '[^\\x00-\\x7F]') AND LENGTH(V_FAMILY_NAME) < 50 AND REGEXP_LIKE(V_GIVEN_NAME, '[^\\x00-\\x7F]') AND LENGTH(V_GIVEN_NAME) < 50";
        } else if ("ENT".equalsIgnoreCase(candidateType)) {
            wlFilter = " V_ENTITY_TYPE != 'I' AND REGEXP_LIKE(V_ENTITY_NAME, '[^\\x00-\\x7F]') AND LENGTH(V_ENTITY_NAME) < 50";
        }
        if (!wlFilter.isEmpty()) {
            filter += " AND " + wlFilter;
        }

        // Build SELECT clause with required columns
        Set<String> columns = new LinkedHashSet<>(requiredColumns);
        columns.add("N_UID"); // Always include UID
        String selectClause = String.join(", ", columns);

        String query = "select " + selectClause + " from " + tableName + filter;
        System.out.println("SQL Query for transliteration generated:: " + query);

        try (PreparedStatement pst = connection.prepareStatement(query);
             ResultSet rs = pst.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            while (rs.next()) {
                RowData row = new RowData();
                for (int i = 1; i <= colCount; i++) {
                    String colName = md.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(colName, value);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<RowData> prepareQueryAndGetTableDataForTranslit(Connection connection, Properties props, String tableName, String candidateType) throws Exception {
        return prepareQueryAndGetTableDataForTranslit(connection, props, tableName, candidateType, Arrays.asList("V_FULL_NAME", "V_ENTITY_NAME"));
    }

    private static List<RowData> splitSemicolonNames(List<RowData> rows, String candidateType, Map<String, String> tokenToColumnMap) {
        List<RowData> splitRows = new ArrayList<>();

        for (RowData row : rows) {
            // Get all semicolon-separated values for ALL columns in tokenToColumnMap
            List<List<String>> allValueLists = new ArrayList<>();
            List<String> allColumns = new ArrayList<>();
            for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                String column = entry.getValue();
                allColumns.add(column);
                String value = asString(row.get(column));
                List<String> values = new ArrayList<>();
                for (String part : value.split(";")) {
                    part = part.trim();
                    if (!part.isEmpty()) {
                        values.add(part);
                    }
                }
                if (values.isEmpty()) values.add(""); // Ensure at least one empty value
                allValueLists.add(values);
            }

            // Generate cartesian product of all column values (including full name)
            List<List<String>> combinations = generateCombinations(allValueLists);

            for (List<String> combo : combinations) {
                RowData newRow = new RowData();
                for (String key : row.keySet()) {
                    newRow.put(key, row.get(key));
                }

                // Set ALL columns to their combo values
                for (int i = 0; i < allColumns.size(); i++) {
                    newRow.put(allColumns.get(i), combo.get(i));
                }

                splitRows.add(newRow);
            }
        }
        return splitRows;
    }

private static void generateRawMessagesForRows(List<RowData> rows, Properties props, JSONObject templateJson, String tableName, String tagName, String webserviceId, String candidateType, List<Map<String, Object>> messages, String wlType, Map<String, String> tokenToColumnMap, List<String> validVariationTokens, String combineVariations, int maxIndStopwords, int maxEntStopwords) {
        Set<String> seenMessages = new HashSet<>();

        for (RowData row : rows) {
            String uid = asString(row.get("N_UID"));

            // Build source and target column strings
            StringBuilder sourceInputBuilder = new StringBuilder();
            StringBuilder targetColumnBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                String column = entry.getValue();
                String value = asString(row.get(column));
                if (sourceInputBuilder.length() > 0) {
                    sourceInputBuilder.append(";");
                    targetColumnBuilder.append(";");
                }
                sourceInputBuilder.append(value);
                targetColumnBuilder.append(column);
            }
            String sourceInput = sourceInputBuilder.toString();
            String targetColumn = targetColumnBuilder.toString();

            // Generate exact with all tokens replaced
            Map<String, String> exactTokenToValue = new HashMap<>();
            for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                exactTokenToValue.put(entry.getKey(), asString(row.get(entry.getValue())));
            }
            JSONObject jsonExact = buildJsonWithAllTokens(templateJson, exactTokenToValue);
            String jsonStrExact = jsonExact.toString();
            String targetInputExact = buildTargetInput(tokenToColumnMap, exactTokenToValue);
            addToArray(jsonStrExact, "EXACT", messages, seenMessages, uid, tableName, wlType, sourceInput, targetInputExact, targetColumn, "EXACT", candidateType, maxIndStopwords, maxEntStopwords);

            // Generate variations
            if (!validVariationTokens.isEmpty()) {
                generateVariationsForRow(row, props, templateJson, tableName, tagName, webserviceId, candidateType, messages, wlType, tokenToColumnMap, validVariationTokens, combineVariations, sourceInput, targetColumn, seenMessages, uid, maxIndStopwords, maxEntStopwords);
            }
        }
    }

    private static void generateVariationsForRow(RowData row, Properties props, JSONObject templateJson, String tableName, String tagName, String webserviceId, String candidateType, List<Map<String, Object>> messages, String wlType, Map<String, String> tokenToColumnMap, List<String> validVariationTokens, String combineVariations, String sourceInput, String targetColumn, Set<String> seenMessages, String uid, int maxIndStopwords, int maxEntStopwords) {
        // Generate variations for CED1, CED2, CED3, REP1, REP2, REP3, STOPWORD, SYNONYM
        String[] variationTypes = {"ced1", "ced2", "ced3", "rep1", "rep2", "rep3"};
        boolean enableStopword = "Y".equalsIgnoreCase(props.getProperty("enableIndStopword")) || "Y".equalsIgnoreCase(props.getProperty("enableEntStopword"));
        boolean enableSynonym = "Y".equalsIgnoreCase(props.getProperty("enableSynonym"));

        if (combineVariations.equals("N")) {
            // Independent variations: for each variation type, for each token, generate variants
            for (String variationType : variationTypes) {
                if ("Y".equalsIgnoreCase(props.getProperty(variationType))) {
                    for (String variationToken : validVariationTokens) {
                        String baseValue = asString(row.get(tokenToColumnMap.get(variationToken)));
                        List<String> variants = generateVariantsForType(variationType, baseValue, candidateType);
                        for (String variant : variants) {
                            Map<String, String> tokenToVariant = new HashMap<>();
                            for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                                tokenToVariant.put(entry.getKey(), entry.getKey().equals(variationToken) ? variant : asString(row.get(entry.getValue())));
                            }
                            JSONObject jsonObj = buildJsonWithAllTokens(templateJson, tokenToVariant);
                            String jsonStr = jsonObj.toString();
                            String targetInput = buildTargetInput(tokenToColumnMap, tokenToVariant);
                            addToArray(jsonStr, variationType.toUpperCase(), messages, seenMessages, uid, tableName, wlType, sourceInput, targetInput, targetColumn, variationType.toUpperCase(), candidateType, maxIndStopwords, maxEntStopwords);
                        }
                    }
                }
            }

            // Stopword variations
            if (enableStopword) {
                List<String> swList = "IND".equalsIgnoreCase(candidateType) ? indStopwordList : entStopwordList;
                for (String sw : swList) {
                    for (String variationToken : validVariationTokens) {
                        String baseValue = asString(row.get(tokenToColumnMap.get(variationToken)));
                        List<String> variants = generateStopwordVariants(baseValue, sw);
                        for (String variant : variants) {
                            Map<String, String> tokenToVariant = new HashMap<>();
                            for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                                tokenToVariant.put(entry.getKey(), entry.getKey().equals(variationToken) ? variant : asString(row.get(entry.getValue())));
                            }
                            JSONObject jsonObj = buildJsonWithAllTokens(templateJson, tokenToVariant);
                            String jsonStr = jsonObj.toString();
                            String targetInput = buildTargetInput(tokenToColumnMap, tokenToVariant);
                            addToArray(jsonStr, "STOPWORD", messages, seenMessages, uid, tableName, wlType, sourceInput, targetInput, targetColumn, "STOPWORD", candidateType, maxIndStopwords, maxEntStopwords);
                        }
                    }
                }
            }

            // Synonym variations
            if (enableSynonym) {
                Map<String, List<String>> synMap = "IND".equalsIgnoreCase(candidateType) ? indSynonymMap : entSynonymMap;
                for (String variationToken : validVariationTokens) {
                    String baseValue = asString(row.get(tokenToColumnMap.get(variationToken)));
                    List<String> variants = generateSynonymVariants(baseValue, synMap);
                    for (String variant : variants) {
                        Map<String, String> tokenToVariant = new HashMap<>();
                        for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                            tokenToVariant.put(entry.getKey(), entry.getKey().equals(variationToken) ? variant : asString(row.get(entry.getValue())));
                        }
                        JSONObject jsonObj = buildJsonWithAllTokens(templateJson, tokenToVariant);
                        String jsonStr = jsonObj.toString();
                        String targetInput = buildTargetInput(tokenToColumnMap, tokenToVariant);
                        addToArray(jsonStr, "SYNONYM", messages, seenMessages, uid, tableName, wlType, sourceInput, targetInput, targetColumn, "SYNONYM", candidateType, maxIndStopwords, maxEntStopwords);
                    }
                }
            }
        } else {
            // Combined variations: for each variation type, generate cartesian product of variants across all tokens
            for (String variationType : variationTypes) {
                if ("Y".equalsIgnoreCase(props.getProperty(variationType))) {
                    List<List<String>> allVariantsLists = new ArrayList<>();
                    for (String variationToken : validVariationTokens) {
                        String baseValue = asString(row.get(tokenToColumnMap.get(variationToken)));
                        List<String> variants = generateVariantsForType(variationType, baseValue, candidateType);
                        allVariantsLists.add(variants);
                    }
                    List<List<String>> variantCombos = generateCombinations(allVariantsLists);
                    for (List<String> combo : variantCombos) {
                        Map<String, String> tokenToVariant = new HashMap<>();
                        for (int i = 0; i < validVariationTokens.size(); i++) {
                            tokenToVariant.put(validVariationTokens.get(i), combo.get(i));
                        }
                        for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                            if (!validVariationTokens.contains(entry.getKey())) {
                                tokenToVariant.put(entry.getKey(), asString(row.get(entry.getValue())));
                            }
                        }
                        JSONObject jsonObj = buildJsonWithAllTokens(templateJson, tokenToVariant);
                        String jsonStr = jsonObj.toString();
                        String targetInput = buildTargetInput(tokenToColumnMap, tokenToVariant);
                        addToArray(jsonStr, variationType.toUpperCase(), messages, seenMessages, uid, tableName, wlType, sourceInput, targetInput, targetColumn, variationType.toUpperCase(), candidateType, maxIndStopwords, maxEntStopwords);
                    }
                }
            }

            // Combined stopwords
            if (enableStopword) {
                List<String> swList = "IND".equalsIgnoreCase(candidateType) ? indStopwordList : entStopwordList;
                for (String sw : swList) {
                    List<List<String>> allVariantsLists = new ArrayList<>();
                    for (String variationToken : validVariationTokens) {
                        String baseValue = asString(row.get(tokenToColumnMap.get(variationToken)));
                        List<String> variants = generateStopwordVariants(baseValue, sw);
                        allVariantsLists.add(variants);
                    }
                    List<List<String>> variantCombos = generateCombinations(allVariantsLists);
                    for (List<String> combo : variantCombos) {
                        Map<String, String> tokenToVariant = new HashMap<>();
                        for (int i = 0; i < validVariationTokens.size(); i++) {
                            tokenToVariant.put(validVariationTokens.get(i), combo.get(i));
                        }
                        for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                            if (!validVariationTokens.contains(entry.getKey())) {
                                tokenToVariant.put(entry.getKey(), asString(row.get(entry.getValue())));
                            }
                        }
                        JSONObject jsonObj = buildJsonWithAllTokens(templateJson, tokenToVariant);
                        String jsonStr = jsonObj.toString();
                        String targetInput = buildTargetInput(tokenToColumnMap, tokenToVariant);
                        addToArray(jsonStr, "STOPWORD", messages, seenMessages, uid, tableName, wlType, sourceInput, targetInput, targetColumn, "STOPWORD", candidateType, maxIndStopwords, maxEntStopwords);
                    }
                }
            }

            // Combined synonyms
            if (enableSynonym) {
                Map<String, List<String>> synMap = "IND".equalsIgnoreCase(candidateType) ? indSynonymMap : entSynonymMap;
                List<List<String>> allVariantsLists = new ArrayList<>();
                for (String variationToken : validVariationTokens) {
                    String baseValue = asString(row.get(tokenToColumnMap.get(variationToken)));
                    List<String> variants = generateSynonymVariants(baseValue, synMap);
                    allVariantsLists.add(variants);
                }
                List<List<String>> variantCombos = generateCombinations(allVariantsLists);
                for (List<String> combo : variantCombos) {
                    Map<String, String> tokenToVariant = new HashMap<>();
                    for (int i = 0; i < validVariationTokens.size(); i++) {
                        tokenToVariant.put(validVariationTokens.get(i), combo.get(i));
                    }
                    for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                        if (!validVariationTokens.contains(entry.getKey())) {
                            tokenToVariant.put(entry.getKey(), asString(row.get(entry.getValue())));
                        }
                    }
                        JSONObject jsonObj = buildJsonWithAllTokens(templateJson, tokenToVariant);
                        String jsonStr = jsonObj.toString();
                        String targetInput = buildTargetInput(tokenToColumnMap, tokenToVariant);
                        addToArray(jsonStr, "SYNONYM", messages, seenMessages, uid, tableName, wlType, sourceInput, targetInput, targetColumn, "SYNONYM", candidateType, maxIndStopwords, maxEntStopwords);
                }
            }
        }
    }

    private static List<String> generateVariantsForType(String variationType, String baseValue, String candidateType) {
        switch (variationType) {
            case "ced1": return generateCedVariants(baseValue, 1);
            case "ced2": return generateCedVariants(baseValue, 2);
            case "ced3": return generateCedVariants(baseValue, 3);
            case "rep1": return generateRepVariants(baseValue, 1);
            case "rep2": return generateRepVariants(baseValue, 2);
            case "rep3": return generateRepVariants(baseValue, 3);
            default: return new ArrayList<>();
        }
    }

    private static void generateTranslitMessagesForRows(List<RowData> rows, Properties props, JSONObject templateJson, String tableName, String tagName, String webserviceId, String candidateType, List<Map<String, Object>> messages, String wlType, Map<String, String> tokenToColumnMap, List<String> validVariationTokens, String combineVariations) {
        Set<String> seenMessages = new HashSet<>();

        for (RowData row : rows) {
            String uid = asString(row.get("N_UID"));

            // Build source and target column strings
            StringBuilder sourceInputBuilder = new StringBuilder();
            StringBuilder targetInputBuilder = new StringBuilder();
            StringBuilder targetColumnBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                String token = entry.getKey();
                String column = entry.getValue();
                String value = asString(row.get(column));
                if (sourceInputBuilder.length() > 0) {
                    sourceInputBuilder.append(";");
                    targetInputBuilder.append(";");
                    targetColumnBuilder.append(";");
                }
                sourceInputBuilder.append(value);
                targetInputBuilder.append(value); // Transliteration doesn't change values
                targetColumnBuilder.append(column);
            }
            String sourceInput = sourceInputBuilder.toString();
            String targetColumn = targetColumnBuilder.toString();

            // Generate translit with all tokens replaced
            Map<String, String> tokenToValue = new HashMap<>();
            for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
                tokenToValue.put(entry.getKey(), asString(row.get(entry.getValue())));
            }
            JSONObject json = buildJsonWithAllTokens(templateJson, tokenToValue);
            String jsonStr = json.toString();
            addToArray(jsonStr, "TRANSLIT", messages, seenMessages, uid, tableName, wlType, sourceInput, targetInputBuilder.toString(), targetColumn, "TRANSLIT", candidateType, 0, 0);
        }
    }

    private static JSONObject buildJsonWithAllTokens(JSONObject templateJson, Map<String, String> tokenToVariantValue) {
        JSONObject jsonObj = new JSONObject(templateJson.toString()); // deep copy
        JSONObject candidate = jsonObj.getJSONObject("requestJson").getJSONArray("Candidate").getJSONObject(0);

        // Replace all tokens with their values
        for (Map.Entry<String, String> entry : tokenToVariantValue.entrySet()) {
            String token = entry.getKey();
            String value = entry.getValue();

            // Determine the JSON field for the token
            String field = getJsonFieldForToken(token);
            candidate.put(field, value);
        }

        return jsonObj;
    }

    private static String getJsonFieldForToken(String token) {
        switch (token) {
            case "__FULL_NAME__": return "Full Name";
            case "__ORG_NAME__": return "Organization Name";
            case "__FIRST_NAME__": return "First Name";
            case "__LAST_NAME__": return "Last Name";
            case "__DATE_OF_BIRTHS__": return "Date Of Birth";
            // Add more as needed
            default: return "Full Name"; // fallback
        }
    }

    private static String buildTargetInput(Map<String, String> tokenToColumnMap, Map<String, String> tokenToVariantValue) {
        StringBuilder targetInputBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : tokenToColumnMap.entrySet()) {
            String token = entry.getKey();
            String value = tokenToVariantValue.get(token);
            if (targetInputBuilder.length() > 0) {
                targetInputBuilder.append(";");
            }
            targetInputBuilder.append(value);
        }
        return targetInputBuilder.toString();
    }

private static String buildJson(String template, String name, String candidateType, String uid, String tableName, String wlType) {
        JSONObject json = new JSONObject(template);
        String field = "IND".equalsIgnoreCase(candidateType) ? "Full Name" : "Organization Name"; // Based on json structure
        json.getJSONObject("requestJson").getJSONArray("Candidate").getJSONObject(0).put(field, name);

        JSONObject additional = new JSONObject();
        additional.put("uid", uid);
        additional.put("table", tableName);
        additional.put("watchlistType", wlType);
            json.put(ConstantsCS.ADDITIONAL_DATA, additional);

        return json.toString();
    }

    private static void addToArray(String jsonStr, String variantType, List<Map<String, Object>> messages, Set<String> seen, String uid, String tableName, String wlType, String sourceInput, String targetInput, String targetColumn, String type, String candidateType, int maxIndStopwords, int maxEntStopwords) {
        if (seen.add(jsonStr + type)) {  // Unique by json + type
            if (variantType.equals("STOPWORD")) {
                boolean canAdd = false;
                if ("IND".equalsIgnoreCase(candidateType)) {
                    if (maxIndStopwords == 0 || indStopwordCount < maxIndStopwords) {
                        canAdd = true;
                        indStopwordCount++;
                    } else {
                        System.out.println("IND STOPWORD cap reached (" + indStopwordCount + "), skipping further.");
                    }
                } else if ("ENT".equalsIgnoreCase(candidateType)) {
                    if (maxEntStopwords == 0 || entStopwordCount < maxEntStopwords) {
                        canAdd = true;
                        entStopwordCount++;
                    } else {
                        System.out.println("ENT STOPWORD cap reached (" + entStopwordCount + "), skipping further.");
                    }
                } else {
                    canAdd = true; // For other types, no cap
                }
                if (!canAdd) return;
            }
            Map<String, Object> meta = new HashMap<>();
            meta.put("json", jsonStr);
            meta.put("variantType", variantType);
            meta.put("uid", uid);
            meta.put("watchlist", wlType);
            meta.put("sourceInput", sourceInput);
            meta.put("targetInput", targetInput);
            meta.put("targetColumn", targetColumn);
            messages.add(meta);
        }
    }

private static List<String> generateCedVariants(String input, int n) {
        List<String> variants = new ArrayList<>();
        int len = input.length();
        if (len <= n) return variants;

        // Start
        variants.add(input.substring(n));

        // Middle
        int startMid = (len - n) / 2;
        variants.add(input.substring(0, startMid) + input.substring(startMid + n));

        // End
        variants.add(input.substring(0, len - n));

        return variants;
    }

    private static List<String> generateRepVariants(String input, int n) {
        List<String> variants = new ArrayList<>();
        int len = input.length();
        if (len <= n) return variants;

        SecureRandom random = new SecureRandom();

        // Start
        String repStart = randomLetters(n, random) + input.substring(n);
        variants.add(repStart);

        // End
        String repEnd = input.substring(0, len - n) + randomLetters(n, random);
        variants.add(repEnd);

        // Middle
        int startMid = (len - n) / 2;
        String repMid = input.substring(0, startMid) + randomLetters(n, random) + input.substring(startMid + n);
        variants.add(repMid);

        return variants;
    }

    private static String randomLetters(int length, SecureRandom random) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = (char) ('a' + random.nextInt(26));
            sb.append(random.nextBoolean() ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

private static List<String> generateStopwordVariants(String fullName, String sw) {
        List<String> variants = new ArrayList<>();
        variants.add(sw + " " + fullName); // begin
        variants.add(fullName + " " + sw); // end

        String[] tokens = fullName.split("\\s+");
        for (int i = 1; i < tokens.length; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i; j++) sb.append(tokens[j]).append(" ");
            sb.append(sw).append(" ");
            for (int j = i; j < tokens.length; j++) sb.append(tokens[j]).append(" ");
            variants.add(sb.toString().trim());
        }
        return variants;
    }

private static List<String> generateSynonymVariants(String fullName, Map<String, List<String>> synMap) {
        List<String> variants = new ArrayList<>();
        String[] words = fullName.split("\\s+");
        List<List<String>> lists = new ArrayList<>();
        for (String word : words) {
            List<String> opts = new ArrayList<>();
            opts.add(word);
            List<String> syns = synMap.get(word.toUpperCase());
            if (syns != null) {
                opts.addAll(syns);
                System.out.println("Found synonyms for '" + word + "': " + syns);
            } else {
                System.out.println("No synonyms found for '" + word + "'");
            }
            lists.add(opts);
        }
        List<List<String>> combos = generateCombinations(lists);
        System.out.println("Total combinations for '" + fullName + "': " + combos.size());
        for (List<String> combo : combos) {
            String var = String.join(" ", combo);
            if (!var.equals(fullName)) variants.add(var);
        }
        System.out.println("Final synonym variants: " + variants.size());
        return variants;
    }

    private static List<List<String>> generateCombinations(List<List<String>> lists) {
        List<List<String>> result = new ArrayList<>();
        generateCombinationsHelper(lists, 0, new ArrayList<>(), result);
        return result;
    }

    private static void generateCombinationsHelper(List<List<String>> lists, int index, List<String> current, List<List<String>> result) {
        if (index == lists.size()) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (String item : lists.get(index)) {
            current.add(item);
            generateCombinationsHelper(lists, index + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    // generateCombinations as before

    private static boolean isNonEnglish(String str) {
        return str.matches(".*[^\\x00-\\x7F].*");
    }

    private static String loadJsonFromFile(String filePath, Properties props) {
        try {
            File file = new File(filePath);
            String jsonContent = Files.readString(Path.of(file.getPath()));
            JSONObject jsonObject = new JSONObject(jsonContent);

            String candidateJurisdiction = props.getProperty("candidateJurisdiction", "");
            String businessDomain = props.getProperty("businessDomain", "");

            JSONObject requestJson = jsonObject.getJSONObject("requestJson");
            JSONArray candidates = requestJson.getJSONArray("Candidate");
            if (candidates.length() > 0) {
                JSONObject candidateObj = candidates.getJSONObject(0);
                candidateObj.put("Candidate Jurisdiction", candidateJurisdiction);
                candidateObj.put("Business Domain", businessDomain);
            }

            return jsonObject.toString(4);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

private static void writeJsonAsExcelFile(List<Map<String, Object>> messages, String transactionService, String tagName, String webService, String candidateType) throws IOException {
        if (!ConstantsCS.OUTPUT_FOLDER.exists()) {
            ConstantsCS.OUTPUT_FOLDER.mkdirs();
        }

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Output");

        String thirdColumn = ConstantsCS.MESSAGE + "CS";
        String[] headers = {
                ConstantsCS.SEQ_NO,
                ConstantsCS.RULE,
                thirdColumn,
                ConstantsCS.SOURCE_INPUT,
                ConstantsCS.TARGET_INPUT,
                ConstantsCS.TARGET_COLUMN,
                ConstantsCS.WATCHLIST,
                ConstantsCS.NUID
        };

        Row headerRow = sheet.createRow(0);
        XSSFCellStyle headStyle = (XSSFCellStyle) workbook.createCellStyle();
        XSSFColor headerColor = new XSSFColor(new byte[]{(byte)162, (byte)196, (byte)201}, null);
        headStyle.setFillForegroundColor(headerColor);
        headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        headStyle.setFont(font);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headStyle);
        }

        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> meta = messages.get(i);
            String rawMessage = (String) meta.get("json");
            if (rawMessage.length() > ConstantsCS.MAX_MSG_LEN) rawMessage = "[message exceeds " + ConstantsCS.MAX_MSG_LEN + " chars]";

            String uid = (String) meta.get("uid");
            String variantType = (String) meta.get("variantType");
            String watchlist = (String) meta.get("watchlist");

            String ruleName = variantType;

            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(i + 1);
            row.createCell(1).setCellValue(ruleName);
            row.createCell(2).setCellValue(rawMessage);
            row.createCell(3).setCellValue((String) meta.get("sourceInput"));
            row.createCell(4).setCellValue((String) meta.get("targetInput"));
            row.createCell(5).setCellValue((String) meta.get("targetColumn"));
            row.createCell(6).setCellValue(watchlist);
            row.createCell(7).setCellValue(uid);
        }

        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 4000);
        sheet.setColumnWidth(3, 4000);
        sheet.setColumnWidth(4, 4000);
        sheet.setColumnWidth(5, 4000);
        sheet.setColumnWidth(6, 4000);
        sheet.setColumnWidth(7, 4000);

        sheet.createFreezePane(0, 1);

        FileOutputStream fileOut = new FileOutputStream(ConstantsCS.OUTPUT_XLSX_FILE_PATH);
        workbook.write(fileOut);
        fileOut.close();
        workbook.close();

        System.out.println("Successfully wrote to Excel (" + ConstantsCS.OUTPUT_FILE_NAME + ".xlsx) file.");
    }

    private static void writeRawMessagesToJsonFile(List<Map<String, Object>> messages) throws IOException {
        if (!ConstantsCS.OUTPUT_FOLDER.exists()) {
            ConstantsCS.OUTPUT_FOLDER.mkdirs();
        }

        File outputFile = new File(ConstantsCS.OUTPUT_FOLDER, ConstantsCS.OUTPUT_FILE_NAME + ".json");

        JSONArray cleanArray = new JSONArray();
        for (Map<String, Object> meta : messages) {
            String jsonStr = (String) meta.get("json");
            cleanArray.put(new JSONObject(jsonStr));
        }
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(cleanArray.toString(4).getBytes(ConstantsCS.ENCODER));
        }

        System.out.println("Successfully wrote raw messages to JSON (" + ConstantsCS.OUTPUT_FILE_NAME + ".json) file.");
    }
}
