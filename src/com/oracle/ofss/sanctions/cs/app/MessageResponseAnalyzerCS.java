package com.oracle.ofss.sanctions.cs.app;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

public class MessageResponseAnalyzerCS {
    public static void performAnalysis(String engine) throws Exception {
        System.out.println("Performing analysis for engine: " + engine);
        Properties props = new Properties();
        try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
            props.load(reader);
        }

        ZipSecureFile.setMinInflateRatio(0.001);
        try (FileInputStream fis = new FileInputStream(ConstantsCS.OUTPUT_XLSX_FILE_PATH);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            String rulesetHeader = engine + " RULESET_RESULTS";
            String responseHeader = engine + " RESPONSE";
            int rulesetCol = -1;
            int responseCol = -1;
            int sourceInputCol = -1, targetInputCol = -1, targetColumnCol = -1, watchlistCol = -1, nuidCol = -1;

            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String header = headerRow.getCell(i).getStringCellValue();
                if (rulesetHeader.equals(header)) rulesetCol = i;
                else if (responseHeader.equals(header)) responseCol = i;
                else if ("Source Input".equalsIgnoreCase(header)) sourceInputCol = i;
                else if ("Target Input".equalsIgnoreCase(header)) targetInputCol = i;
                else if ("Target Column".equalsIgnoreCase(header)) targetColumnCol = i;
                else if ("Watchlist".equalsIgnoreCase(header)) watchlistCol = i;
                else if ("N_UID".equalsIgnoreCase(header)) nuidCol = i;
            }

            if (rulesetCol == -1) {
                System.out.println("RULESET_RESULTS column for " + engine + " not found, skipping analysis.");
                return;
            }

            // Add RESPONSE column if not exists after RULESET_RESULTS
            if (responseCol == -1) {
                responseCol = rulesetCol + 1;
                headerRow.createCell(responseCol).setCellValue(responseHeader);
                System.out.println("Added " + responseHeader + " at col " + responseCol);
            }

            // Add ordered analyzer columns: SAN, PEP, EDD, PRB, TOTAL, Status
            int sanCol = rulesetCol + 2;
            int pepCol = sanCol + 1;
            int eddCol = pepCol + 1;
            int prbCol = eddCol + 1;
            int totalCol = prbCol + 1;
            int statusCol = totalCol + 1;

            headerRow.createCell(sanCol).setCellValue(engine + "_SAN_MATCH");
            headerRow.createCell(pepCol).setCellValue(engine + "_PEP_MATCH");
            headerRow.createCell(eddCol).setCellValue(engine + "_EDD_MATCH");
            headerRow.createCell(prbCol).setCellValue(engine + "_PRB_MATCH");
            headerRow.createCell(totalCol).setCellValue(engine + "_MATCH_COUNT");
            headerRow.createCell(statusCol).setCellValue(engine + " Status");

            System.out.println("Added analyzer columns for " + engine + " from col " + sanCol + " to " + statusCol);

            // Apply uniform header style
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                if (headerRow.getCell(i) != null && headerRow.getCell(0).getCellStyle() != null) {
                    headerRow.getCell(i).setCellStyle(headerRow.getCell(0).getCellStyle());
                }
            }

            // Process rows
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                String resultJson = row.getCell(rulesetCol).getStringCellValue();
                int[] categorized = categorizeMatchCounts(resultJson);

                row.createCell(sanCol).setCellValue(categorized[0]);
                row.createCell(pepCol).setCellValue(categorized[1]);
                row.createCell(eddCol).setCellValue(categorized[2]);
                row.createCell(prbCol).setCellValue(categorized[3]);
                row.createCell(totalCol).setCellValue(sumMatchCount(new org.json.JSONObject(resultJson.isEmpty() ? "{}" : resultJson)));

                // Status
                boolean pass = false;
                if (sourceInputCol != -1 && targetColumnCol != -1 && watchlistCol != -1 && nuidCol != -1) {
                    String targetColumn = row.getCell(targetColumnCol).getStringCellValue();
                    String watchlist = row.getCell(watchlistCol).getStringCellValue();
                    String n_uid = row.getCell(nuidCol).getStringCellValue();
                    pass = checkMatch(resultJson, watchlist, n_uid, targetColumn, engine);
                }
                setPassFail(row.createCell(statusCol), pass, workbook);
            }

            try (FileOutputStream fos = new FileOutputStream(ConstantsCS.OUTPUT_XLSX_FILE_PATH)) {
                workbook.write(fos);
            }
        }
    }

    public static void formatExcel() throws Exception {
        try {
            System.out.println("\n=============================================================");
            System.out.println("                  FORMATTING EXCEL STARTED                   ");
            System.out.println("=============================================================");

            Properties props = new Properties();
            try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
                props.load(reader);
            } catch (IOException e) {
                System.err.println("Error reading properties file: " + e.getMessage());
                throw e;
            }

            try (FileInputStream fis = new FileInputStream(ConstantsCS.OUTPUT_XLSX_FILE_PATH);
                 Workbook workbook = new XSSFWorkbook(fis)) {

                Sheet sheet = workbook.getSheetAt(0);

                // Calculate max length for each column and set width
                Row headerRow = sheet.getRow(0);
                if (headerRow != null) {
                    for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                        int maxLen = 0;
                        int maxWordCount = 0;
                        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                            Row row = sheet.getRow(rowIndex);
                            if (row != null) {
                                Cell cell = row.getCell(i);
                                if (cell != null) {
                                    String cellValue = "";
                                    switch (cell.getCellType()) {
                                        case STRING:
                                            cellValue = cell.getStringCellValue();
                                            break;
                                        case NUMERIC:
                                            cellValue = String.valueOf(cell.getNumericCellValue());
                                            break;
                                        case BOOLEAN:
                                            cellValue = String.valueOf(cell.getBooleanCellValue());
                                            break;
                                        default:
                                            cellValue = "";
                                            break;
                                    }
                                    if (cellValue != null) {
                                        if (cellValue.length() > maxLen) {
                                            maxLen = cellValue.length();
                                        }
                                        int wordCount = cellValue.trim().isEmpty() ? 0 : cellValue.split("\\s+").length;
                                        if (wordCount > maxWordCount) {
                                            maxWordCount = wordCount;
                                        }
                                    }
                                }
                            }
                        }
                        if (maxWordCount > 5) {
                            maxLen = Math.min(maxLen, 40); // Cap at ~40 chars for 4-5 words
                        }
                        if (maxLen > 255) maxLen = 255;
                        sheet.setColumnWidth(i, Math.min(maxLen * 256 + 2000, 255 * 256));
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(ConstantsCS.OUTPUT_XLSX_FILE_PATH)) {
                    workbook.write(fos);
                }

            }

            System.out.println("\n=============================================================");
            System.out.println("                   FORMATTING EXCEL COMPLETED                ");
            System.out.println("=============================================================");

        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Something went wrong while formatting Excel", e);
        }
    }

    public static void compareResultsAndUpdateExcel() throws Exception {
        try {
            long startTime = System.currentTimeMillis();
            System.out.println("\n=============================================================");
            System.out.println("              COMPARING RESULTS AND UPDATING EXCEL            ");
            System.out.println("=============================================================");

            Properties props = new Properties();
            try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
                props.load(reader);
            }

            String toggle = props.getProperty("toggle", "N");
            String currentEngine = props.getProperty("matchingEngine", "OS");

            try (FileInputStream fis = new FileInputStream(ConstantsCS.OUTPUT_XLSX_FILE_PATH);
                 Workbook workbook = new XSSFWorkbook(fis)) {

                Sheet sheet = workbook.getSheetAt(0);
                Row headerRow = sheet.getRow(0);

                // Find column indices for OS RULESET_RESULTS and OT RULESET_RESULTS
                int osResultCol = -1;
                int otResultCol = -1;
                int osMatchCountCol = -1;
                int otMatchCountCol = -1;

                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    String header = headerRow.getCell(i).getStringCellValue();
                    if ("OS RULESET_RESULTS".equals(header)) {
                        osResultCol = i;
                    } else if ("OT RULESET_RESULTS".equals(header)) {
                        otResultCol = i;
                    } else if ("OS_MATCH_COUNT".equals(header)) {
                        osMatchCountCol = i;
                    } else if ("OT_MATCH_COUNT".equals(header)) {
                        otMatchCountCol = i;
                    }
                }

                if (osResultCol == -1 || otResultCol == -1) {
                    System.out.println("OS RULESET_RESULTS or OT RULESET_RESULTS columns not found. Skipping comparison.");
                    return;
                }

                // Find or create OS_MATCH_COUNT and OT_MATCH_COUNT columns
                int nextColIndex = headerRow.getLastCellNum();
                if (osMatchCountCol == -1) {
                    osMatchCountCol = nextColIndex++;
                    org.apache.poi.ss.usermodel.Cell osMatchHeader = headerRow.createCell(osMatchCountCol);
                    osMatchHeader.setCellValue("OS_MATCH_COUNT");
                    if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                        osMatchHeader.setCellStyle(headerRow.getCell(0).getCellStyle());
                    }
                }
                if (otMatchCountCol == -1) {
                    otMatchCountCol = nextColIndex++;
                    org.apache.poi.ss.usermodel.Cell otMatchHeader = headerRow.createCell(otMatchCountCol);
                    otMatchHeader.setCellValue("OT_MATCH_COUNT");
                    if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                        otMatchHeader.setCellStyle(headerRow.getCell(0).getCellStyle());
                    }
                }

                // Find existing categorized match columns (added by performAnalysis)
                int osSanMatchCol = -1, osPepMatchCol = -1, osEddMatchCol = -1, osPrbMatchCol = -1;
                int otSanMatchCol = -1, otPepMatchCol = -1, otEddMatchCol = -1, otPrbMatchCol = -1;

                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    String header = headerRow.getCell(i).getStringCellValue();
                    if ("OS_SAN_MATCH".equals(header)) osSanMatchCol = i;
                    else if ("OS_PEP_MATCH".equals(header)) osPepMatchCol = i;
                    else if ("OS_EDD_MATCH".equals(header)) osEddMatchCol = i;
                    else if ("OS_PRB_MATCH".equals(header)) osPrbMatchCol = i;
                    else if ("OT_SAN_MATCH".equals(header)) otSanMatchCol = i;
                    else if ("OT_PEP_MATCH".equals(header)) otPepMatchCol = i;
                    else if ("OT_EDD_MATCH".equals(header)) otEddMatchCol = i;
                    else if ("OT_PRB_MATCH".equals(header)) otPrbMatchCol = i;
                }

                // Add new columns for OS/OT comparison
                int commonCol = nextColIndex++;
                int missingCol = nextColIndex++;
                int additionalCol = nextColIndex++;
                int finalStatusCol = nextColIndex++;

                org.apache.poi.ss.usermodel.Cell commonHeader = headerRow.createCell(commonCol);
                commonHeader.setCellValue("Common Matches");
                if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                    commonHeader.setCellStyle(headerRow.getCell(0).getCellStyle());
                }

                org.apache.poi.ss.usermodel.Cell missingHeader = headerRow.createCell(missingCol);
                missingHeader.setCellValue("OS Missing Expected match in OT");
                if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                    missingHeader.setCellStyle(headerRow.getCell(0).getCellStyle());
                }

                org.apache.poi.ss.usermodel.Cell additionalHeader = headerRow.createCell(additionalCol);
                additionalHeader.setCellValue("Additional Matches in OT");
                if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                    additionalHeader.setCellStyle(headerRow.getCell(0).getCellStyle());
                }

                org.apache.poi.ss.usermodel.Cell finalStatusHeader = headerRow.createCell(finalStatusCol);
                finalStatusHeader.setCellValue("Final Status");
                if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                    finalStatusHeader.setCellStyle(headerRow.getCell(0).getCellStyle());
                }

                // Freeze header row
                sheet.createFreezePane(0, 1);

                // Process each data row
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) continue;

                    String osResult = row.getCell(osResultCol).getStringCellValue();
                    String otResult = row.getCell(otResultCol).getStringCellValue();

                    // Calculate match counts from RULESET_RESULTS
                    int osMatchCount = 0;
                    int otMatchCount = 0;
                    try {
                        if (osResult != null && !osResult.isEmpty()) {
                            org.json.JSONObject osObj = new org.json.JSONObject(osResult);
                            osMatchCount = sumMatchCount(osObj);
                        }
                        if (otResult != null && !otResult.isEmpty()) {
                            org.json.JSONObject otObj = new org.json.JSONObject(otResult);
                            otMatchCount = sumMatchCount(otObj);
                        }
                    } catch (Exception e) {
                        // Keep defaults if parsing fails
                        System.err.println("Error calculating match counts: " + e.getMessage());
                    }

                    // Write match counts to columns
                    row.createCell(osMatchCountCol).setCellValue(osMatchCount);
                    row.createCell(otMatchCountCol).setCellValue(otMatchCount);

                    // Calculate categorized match counts
                    int[] osCategorizedCounts = categorizeMatchCounts(osResult);
                    int[] otCategorizedCounts = categorizeMatchCounts(otResult);

                    // Write categorized counts to columns (only if columns were added)
                    if (osSanMatchCol != -1) row.createCell(osSanMatchCol).setCellValue(osCategorizedCounts[0]); // SAN
                    if (osPepMatchCol != -1) row.createCell(osPepMatchCol).setCellValue(osCategorizedCounts[1]); // PEP
                    if (osEddMatchCol != -1) row.createCell(osEddMatchCol).setCellValue(osCategorizedCounts[2]); // EDD
                    if (osPrbMatchCol != -1) row.createCell(osPrbMatchCol).setCellValue(osCategorizedCounts[3]); // PRB

                    if (otSanMatchCol != -1) row.createCell(otSanMatchCol).setCellValue(otCategorizedCounts[0]); // SAN
                    if (otPepMatchCol != -1) row.createCell(otPepMatchCol).setCellValue(otCategorizedCounts[1]); // PEP
                    if (otEddMatchCol != -1) row.createCell(otEddMatchCol).setCellValue(otCategorizedCounts[2]); // EDD
                    if (otPrbMatchCol != -1) row.createCell(otPrbMatchCol).setCellValue(otCategorizedCounts[3]); // PRB

                    // New OS/OT comparison logic
                    if ("Y".equals(props.getProperty("analyzeAfterToggle", "N"))) {
                        RulesetMatches osMatches = parseToMatches(osResult, "OS");
                        RulesetMatches otMatches = parseToMatches(otResult, "OT");

                        java.util.Map<String, java.util.List<MatchObject>> commonByType = new java.util.HashMap<>();
                        java.util.Map<String, java.util.List<MatchObject>> osMissingByType = new java.util.HashMap<>();
                        java.util.Map<String, java.util.List<MatchObject>> otAdditionalByType = new java.util.HashMap<>();

                        for (String type : new String[]{"SAN", "PEP", "EDD", "PRB"}) {
                            java.util.List<MatchObject> osList = osMatches.matchesByType.getOrDefault(type, new java.util.ArrayList<>());
                            java.util.List<MatchObject> otList = otMatches.matchesByType.getOrDefault(type, new java.util.ArrayList<>());

                            java.util.List<MatchObject> common = new java.util.ArrayList<>();
                            java.util.List<MatchObject> osRemaining = new java.util.ArrayList<>(osList);
                            java.util.List<MatchObject> otRemaining = new java.util.ArrayList<>(otList);

                            for (int i = osRemaining.size() - 1; i >= 0; i--) {
                                MatchObject osMatch = osRemaining.get(i);
                                int idx = otRemaining.indexOf(osMatch);
                                if (idx != -1) {
                                    common.add(osMatch);
                                    osRemaining.remove(i);
                                    otRemaining.remove(idx);
                                }
                            }

                            if (!common.isEmpty()) commonByType.put(type, common);
                            if (!osRemaining.isEmpty()) osMissingByType.put(type, osRemaining);
                            if (!otRemaining.isEmpty()) otAdditionalByType.put(type, otRemaining);
                        }

                        // Create JSON strings
                        org.json.JSONObject commonJson = new org.json.JSONObject();
                        for (java.util.Map.Entry<String, java.util.List<MatchObject>> entry : commonByType.entrySet()) {
                            org.json.JSONArray arr = new org.json.JSONArray();
                            for (MatchObject mo : entry.getValue()) arr.put(new org.json.JSONObject(mo.toString()));
                            commonJson.put(entry.getKey(), arr);
                        }

                        org.json.JSONObject missingJson = new org.json.JSONObject();
                        for (java.util.Map.Entry<String, java.util.List<MatchObject>> entry : osMissingByType.entrySet()) {
                            org.json.JSONArray arr = new org.json.JSONArray();
                            for (MatchObject mo : entry.getValue()) arr.put(new org.json.JSONObject(mo.toString()));
                            missingJson.put(entry.getKey(), arr);
                        }

                        org.json.JSONObject additionalJson = new org.json.JSONObject();
                        for (java.util.Map.Entry<String, java.util.List<MatchObject>> entry : otAdditionalByType.entrySet()) {
                            org.json.JSONArray arr = new org.json.JSONArray();
                            for (MatchObject mo : entry.getValue()) arr.put(new org.json.JSONObject(mo.toString()));
                            additionalJson.put(entry.getKey(), arr);
                        }

                        // Write to columns
                        row.createCell(commonCol).setCellValue(commonJson.toString());
                        row.createCell(missingCol).setCellValue(missingJson.toString());
                        row.createCell(additionalCol).setCellValue(additionalJson.toString());

                        // Determine final status
                        boolean hasCommon = !commonByType.isEmpty();
                        boolean hasMissing = !osMissingByType.isEmpty();
                        boolean hasAdditional = !otAdditionalByType.isEmpty();

                        String status;
                        if (!hasCommon) {
                            status = "Missing Required";
                        } else if (!hasMissing && !hasAdditional) {
                            status = "Exact";
                        } else if (!hasMissing && hasAdditional) {
                            status = "Exact - Additional OT Matches";
                        } else {
                            status = "Missing Required Matches";
                        }

                        Cell statusCell = row.createCell(finalStatusCol);
                        statusCell.setCellValue(status);

                        // Set color
                        org.apache.poi.ss.usermodel.CellStyle style = workbook.createCellStyle();
                        org.apache.poi.xssf.usermodel.XSSFCellStyle xssfStyle = (org.apache.poi.xssf.usermodel.XSSFCellStyle) style;
                        if (status.equals("Exact") || status.equals("Exact - Additional OT Matches")) {
                            xssfStyle.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)144, (byte)238, (byte)144}, null));
                        } else {
                            xssfStyle.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)255, (byte)182, (byte)193}, null));
                        }
                        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                        statusCell.setCellStyle(style);
                    }
                }

                // Set fixed column widths and uniform header style
                org.apache.poi.ss.usermodel.CellStyle uniformHeadStyle = workbook.createCellStyle();
                if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                    uniformHeadStyle = headerRow.getCell(0).getCellStyle();
                } else {
                    org.apache.poi.xssf.usermodel.XSSFCellStyle xssfHeadStyle = (org.apache.poi.xssf.usermodel.XSSFCellStyle) uniformHeadStyle;
                    xssfHeadStyle.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)162, (byte)196, (byte)201}, null));
                    uniformHeadStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                    org.apache.poi.ss.usermodel.Font font = workbook.createFont();
                    font.setBold(true);
                    uniformHeadStyle.setFont(font);
                }
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    if (headerRow.getCell(i) != null) {
                        headerRow.getCell(i).setCellStyle(uniformHeadStyle);
                    }
                    sheet.setColumnWidth(i, 4000);
                }

                try (FileOutputStream fos = new FileOutputStream(ConstantsCS.OUTPUT_XLSX_FILE_PATH)) {
                    workbook.write(fos);
                }
            }

            System.out.println("\n=============================================================");
            System.out.println("             COMPARISON COMPLETED AND EXCEL UPDATED          ");
            System.out.println("=============================================================");
            long endTime = System.currentTimeMillis();
            System.out.println("Time taken by comparison: " + (endTime - startTime) / 1000L + " seconds");

        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Something went wrong while comparing results", e);
        }
    }

    private static int sumMatchCount(org.json.JSONObject rulesetObj) {
        int total = 0;
        java.util.Iterator<String> keys = rulesetObj.keys();
        while (keys.hasNext()) {
            String rulesetId = keys.next();
            org.json.JSONObject ruleset = rulesetObj.getJSONObject(rulesetId);
            total += ruleset.getInt("matchCount");
        }
        return total;
    }

    private static int[] categorizeMatchCounts(String resultJson) {
        int[] counts = new int[4]; // [SAN, PEP, EDD, PRB]
        if (resultJson == null || resultJson.isEmpty()) {
            return counts;
        }
        try {
            org.json.JSONObject resultObj = new org.json.JSONObject(resultJson);
            java.util.Iterator<String> keys = resultObj.keys();
            while (keys.hasNext()) {
                String rulesetName = keys.next();
                org.json.JSONObject ruleset = resultObj.getJSONObject(rulesetName);
                int matchCount = ruleset.getInt("matchCount");

                if (rulesetName.contains("SAN")) {
                    counts[0] += matchCount; // SAN
                } else if (rulesetName.contains("PEP")) {
                    counts[1] += matchCount; // PEP
                } else if (rulesetName.contains("EDD")) {
                    counts[2] += matchCount; // EDD
                } else if (rulesetName.toLowerCase().contains("country")) {
                    counts[3] += matchCount; // PRB (Country Prohibition)
                }
            }
        } catch (Exception e) {
            System.err.println("Error categorizing match counts: " + e.getMessage());
        }
        return counts;
    }

    private static final java.util.Map<String, java.util.Map<String, String>> wlToIndex = java.util.Map.ofEntries(
        java.util.Map.entry("EU", java.util.Map.of("OS", "idx_european_union", "OT", "FCC_WL_EUROPEAN_UNION_OT")),
        java.util.Map.entry("OFAC", java.util.Map.of("OS", "idx_ofac", "OT", "FCC_WL_OFAC_OT")),
        java.util.Map.entry("UN", java.util.Map.of("OS", "idx_un", "OT", "FCC_WL_UN_OT")),
        java.util.Map.entry("HMT", java.util.Map.of("OS", "idx_hmt", "OT", "FCC_WL_HMT_OT")),
        java.util.Map.entry("DJW", java.util.Map.of("OS", "idx_djw", "OT", "FCC_WL_DJW_OT")),
        java.util.Map.entry("COUNTRY", java.util.Map.of("OS", "idx_tf_dim_country", "OT", "FCC_TF_DIM_COUNTRY_OT")),
        java.util.Map.entry("WCSTANDARD", java.util.Map.of("OS", "idx_wc_standard", "OT", "FCC_WL_WC_STANDARD_OT")),
        java.util.Map.entry("WCPREM", java.util.Map.of("OS", "idx_wc_premium", "OT", "FCC_WL_WC_PREMIUM_OT")),
        java.util.Map.entry("PRV_WL1", java.util.Map.of("OS", "idx_privatelist", "OT", "FCC_WL_PRIVATELIST_OT"))
    );

    // Reverse map for index to watchlist
    private static final java.util.Map<String, String> indexToWatchlist = java.util.Map.ofEntries(
        java.util.Map.entry("idx_european_union", "EU"),
        java.util.Map.entry("FCC_WL_EUROPEAN_UNION_OT", "EU"),
        java.util.Map.entry("idx_ofac", "OFAC"),
        java.util.Map.entry("FCC_WL_OFAC_OT", "OFAC"),
        java.util.Map.entry("idx_un", "UN"),
        java.util.Map.entry("FCC_WL_UN_OT", "UN"),
        java.util.Map.entry("idx_hmt", "HMT"),
        java.util.Map.entry("FCC_WL_HMT_OT", "HMT"),
        java.util.Map.entry("idx_djw", "DJW"),
        java.util.Map.entry("FCC_WL_DJW_OT", "DJW"),
        java.util.Map.entry("idx_tf_dim_country", "COUNTRY"),
        java.util.Map.entry("FCC_TF_DIM_COUNTRY_OT", "COUNTRY"),
        java.util.Map.entry("idx_wc_standard", "WCSTANDARD"),
        java.util.Map.entry("FCC_WL_WC_STANDARD_OT", "WCSTANDARD"),
        java.util.Map.entry("idx_wc_premium", "WCPREM"),
        java.util.Map.entry("FCC_WL_WC_PREMIUM_OT", "WCPREM"),
        java.util.Map.entry("idx_privatelist", "PRV_WL1"),
        java.util.Map.entry("FCC_WL_PRIVATELIST_OT", "PRV_WL1")
    );

    private static class MatchObject {
        String n_uid;
        String watchlist;
        String ruleName;
        java.util.List<String> matchedCols;

        MatchObject(String n_uid, String watchlist, String ruleName, java.util.List<String> matchedCols) {
            this.n_uid = n_uid;
            this.watchlist = watchlist;
            this.ruleName = ruleName;
            this.matchedCols = new ArrayList<>(matchedCols);
            Collections.sort(this.matchedCols);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            MatchObject that = (MatchObject) obj;
            return java.util.Objects.equals(n_uid, that.n_uid) &&
                   java.util.Objects.equals(watchlist, that.watchlist) &&
                   java.util.Objects.equals(ruleName, that.ruleName) &&
                   java.util.Objects.equals(matchedCols, that.matchedCols);  // Exact list match
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(n_uid, watchlist, ruleName, matchedCols);
        }

        @Override
        public String toString() {
            return new org.json.JSONObject()
                .put("n_uid", n_uid)
                .put("watchlist", watchlist)
                .put("ruleName", ruleName)
                .put("matchedCols", new org.json.JSONArray(matchedCols))
                .toString();
        }
    }

    private static class RulesetMatches {
        java.util.Map<String, java.util.List<MatchObject>> matchesByType = new java.util.HashMap<>();
    }

    private static RulesetMatches parseToMatches(String resultJson, String engine) {
        RulesetMatches rms = new RulesetMatches();
        if (resultJson == null || resultJson.isEmpty()) return rms;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(resultJson);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String rulesetId = keys.next();
                org.json.JSONObject ruleset = obj.getJSONObject(rulesetId);
                org.json.JSONArray matches = ruleset.optJSONArray("matches");
                if (matches != null) {
                    for (int i = 0; i < matches.length(); i++) {
                        org.json.JSONObject match = matches.getJSONObject(i);
                        String n_uid = match.optString("n_uid", "");
                        String indexName = match.optString("indexName", "");
                        String watchlist = indexToWatchlist.get(indexName);
                        if (watchlist == null) continue; // Skip unknown index
                        String ruleName = match.optString("ruleName", "");
                        org.json.JSONArray matchedColsJson = match.optJSONArray("matchedCols");
                        java.util.List<String> matchedCols = new java.util.ArrayList<>();
                        if (matchedColsJson != null) {
                            for (int k = 0; k < matchedColsJson.length(); k++) {
                                matchedCols.add(matchedColsJson.getString(k));
                            }
                        }
                        String type = "PRB"; // Default to PRB
                        if (rulesetId.contains("SAN")) type = "SAN";
                        else if (rulesetId.contains("PEP")) type = "PEP";
                        else if (rulesetId.contains("EDD")) type = "EDD";
                        else if (rulesetId.toLowerCase().contains("country")) type = "PRB";
                        rms.matchesByType.computeIfAbsent(type, k -> new java.util.ArrayList<>()).add(new MatchObject(n_uid, watchlist, ruleName, matchedCols));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing to matches: " + e.getMessage());
        }
        return rms;
    }

    private static boolean checkMatch(String resultJson, String watchlist, String n_uid, String targetColumn, String engine) {
        if (resultJson == null || resultJson.isEmpty()) return false;
        java.util.Map<String, String> indexMap = wlToIndex.get(watchlist);
        if (indexMap == null) {
            System.out.println("Debug checkMatch: No indexMap for watchlist=" + watchlist);
            return false;
        }
        String expectedIndex = indexMap.get(engine);
        if (expectedIndex == null) {
            System.out.println("Debug checkMatch: No expectedIndex for engine=" + engine + ", watchlist=" + watchlist);
            return false;
        }

        // Split target columns into a set
        java.util.Set<String> requiredColumns = new java.util.HashSet<>();
        if (targetColumn != null && !targetColumn.isEmpty()) {
            String[] columns = targetColumn.split(";");
            for (String col : columns) {
                requiredColumns.add(col.trim().toUpperCase());
            }
        }
        if (requiredColumns.isEmpty()) return true; // No columns to check, treat as Pass

        System.out.println("Debug checkMatch: watchlist=" + watchlist + ", n_uid=" + n_uid + ", targetColumns=" + requiredColumns + ", engine=" + engine + ", expectedIndex=" + expectedIndex);
        try {
            org.json.JSONObject obj = new org.json.JSONObject(resultJson);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String rulesetId = keys.next();
                org.json.JSONObject ruleset = obj.getJSONObject(rulesetId);
                org.json.JSONArray matches = ruleset.optJSONArray("matches");
                if (matches != null) {
                    for (int i = 0; i < matches.length(); i++) {
                        org.json.JSONObject match = matches.getJSONObject(i);
                        String indexName = match.optString("indexName", "");
                        String matchNuid = match.optString("n_uid", "");

                        // Filter by N_UID and indexName first
                        if (!n_uid.equals(matchNuid) || !expectedIndex.equals(indexName)) {
                            continue;
                        }

                        // Check matchedCols and remove from required set
                        org.json.JSONArray matchedCols = match.optJSONArray("matchedCols");
                        if (matchedCols != null) {
                            for (int k = 0; k < matchedCols.length(); k++) {
                                String matchedCol = matchedCols.getString(k).toUpperCase();
                                requiredColumns.remove(matchedCol);
                            }
                        }

                        System.out.println("Processed match: remaining columns=" + requiredColumns);

                        // Early exit if all columns are covered
                        if (requiredColumns.isEmpty()) {
                            System.out.println("All columns matched for " + watchlist + " in " + engine);
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking match: " + e.getMessage());
            return false;
        }
        System.out.println("Not all columns matched for " + watchlist + " in " + engine + "; remaining=" + requiredColumns);
        return false;
    }

    private static void setPassFail(Cell cell, boolean pass, Workbook workbook) {
        cell.setCellValue(pass ? "Pass" : "Fail");
        org.apache.poi.ss.usermodel.CellStyle style = workbook.createCellStyle();
        org.apache.poi.xssf.usermodel.XSSFCellStyle xssfStyle = (org.apache.poi.xssf.usermodel.XSSFCellStyle) style;
        if (pass) {
            xssfStyle.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)144, (byte)238, (byte)144}, null));
        } else {
            xssfStyle.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)255, (byte)182, (byte)193}, null));
        }
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        cell.setCellStyle(style);
    }
}
