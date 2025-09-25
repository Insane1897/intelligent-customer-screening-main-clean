package com.oracle.ofss.sanctions.cs.app;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class MessageResponseAnalyzerCS {
    public static void performAnalysis(String engine) throws Exception {
        System.out.println("Performing analysis for engine: " + engine);
        Properties props = new Properties();
        try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
            props.load(reader);
        }

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

                // Add new columns for categorized match counts, based on toggle
                int osSanMatchCol = -1, osPepMatchCol = -1, osEddMatchCol = -1, osPrbMatchCol = -1;
                int otSanMatchCol = -1, otPepMatchCol = -1, otEddMatchCol = -1, otPrbMatchCol = -1;

                String[] newHeaders;
                int[] newColIndices;

                if ("Y".equalsIgnoreCase(toggle)) {
                    // Add all OS and OT categorized
                    osSanMatchCol = nextColIndex++;
                    osPepMatchCol = nextColIndex++;
                    osEddMatchCol = nextColIndex++;
                    osPrbMatchCol = nextColIndex++;
                    otSanMatchCol = nextColIndex++;
                    otPepMatchCol = nextColIndex++;
                    otEddMatchCol = nextColIndex++;
                    otPrbMatchCol = nextColIndex++;
                    newHeaders = new String[]{"OS_SAN_MATCH", "OS_PEP_MATCH", "OS_EDD_MATCH", "OS_PRB_MATCH",
                                              "OT_SAN_MATCH", "OT_PEP_MATCH", "OT_EDD_MATCH", "OT_PRB_MATCH"};
                    newColIndices = new int[]{osSanMatchCol, osPepMatchCol, osEddMatchCol, osPrbMatchCol,
                                              otSanMatchCol, otPepMatchCol, otEddMatchCol, otPrbMatchCol};
                } else {
                    // Add only current engine's categorized
                    if ("OS".equalsIgnoreCase(currentEngine)) {
                        osSanMatchCol = nextColIndex++;
                        osPepMatchCol = nextColIndex++;
                        osEddMatchCol = nextColIndex++;
                        osPrbMatchCol = nextColIndex++;
                        newHeaders = new String[]{"OS_SAN_MATCH", "OS_PEP_MATCH", "OS_EDD_MATCH", "OS_PRB_MATCH"};
                        newColIndices = new int[]{osSanMatchCol, osPepMatchCol, osEddMatchCol, osPrbMatchCol};
                    } else {
                        otSanMatchCol = nextColIndex++;
                        otPepMatchCol = nextColIndex++;
                        otEddMatchCol = nextColIndex++;
                        otPrbMatchCol = nextColIndex++;
                        newHeaders = new String[]{"OT_SAN_MATCH", "OT_PEP_MATCH", "OT_EDD_MATCH", "OT_PRB_MATCH"};
                        newColIndices = new int[]{otSanMatchCol, otPepMatchCol, otEddMatchCol, otPrbMatchCol};
                    }
                }

                for (int i = 0; i < newHeaders.length; i++) {
                    org.apache.poi.ss.usermodel.Cell headerCell = headerRow.createCell(newColIndices[i]);
                    headerCell.setCellValue(newHeaders[i]);
                    if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                        headerCell.setCellStyle(headerRow.getCell(0).getCellStyle());
                    }
                }

                // Add new columns for comparison
                int comparisonCol = nextColIndex++;
                int diffPercentCol = nextColIndex++;
                int summaryCol = nextColIndex++;

                // Set headers for new columns with preserved styling
                org.apache.poi.ss.usermodel.Cell comparisonHeader = headerRow.createCell(comparisonCol);
                comparisonHeader.setCellValue("Comparison");
                // Copy header style if available
                if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                    comparisonHeader.setCellStyle(headerRow.getCell(0).getCellStyle());
                }

                org.apache.poi.ss.usermodel.Cell diffHeader = headerRow.createCell(diffPercentCol);
                diffHeader.setCellValue("Difference%");
                if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                    diffHeader.setCellStyle(headerRow.getCell(0).getCellStyle());
                }

                org.apache.poi.ss.usermodel.Cell summaryHeader = headerRow.createCell(summaryCol);
                summaryHeader.setCellValue("Summary");
                if (headerRow.getCell(0) != null && headerRow.getCell(0).getCellStyle() != null) {
                    summaryHeader.setCellStyle(headerRow.getCell(0).getCellStyle());
                }

                // Add status columns if analyzer=Y, right after OS/OT RULESET_RESULTS
                int statusColStart = -1;
                int sourceInputCol = -1;
                int targetInputCol = -1;
                int targetColumnCol = -1;
                int watchlistCol = -1;
                int nuidCol = -1;
                boolean headersFound = false;

                if ("Y".equalsIgnoreCase(props.getProperty("analyzer"))) {
                    for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                        String header = headerRow.getCell(i).getStringCellValue();
                        if ("Source Input".equalsIgnoreCase(header)) sourceInputCol = i;
                        else if ("Target Input".equalsIgnoreCase(header)) targetInputCol = i;
                        else if ("Target Column".equalsIgnoreCase(header)) targetColumnCol = i;
                        else if ("Watchlist".equalsIgnoreCase(header)) watchlistCol = i;
                        else if ("N_UID".equalsIgnoreCase(header)) nuidCol = i;
                    }
                    if (sourceInputCol == -1 || targetInputCol == -1 || targetColumnCol == -1 || watchlistCol == -1 || nuidCol == -1) {
                        headersFound = false;
                        System.out.println("Debug: analyzer=Y, required headers not found (case-insensitive), defaulting to Fail for all rows");
                    } else {
                        headersFound = true;
                        System.out.println("Debug: analyzer=Y, found sourceInputCol=" + sourceInputCol + ", targetInputCol=" + targetInputCol + ", targetColumnCol=" + targetColumnCol + ", watchlistCol=" + watchlistCol + ", nuidCol=" + nuidCol);
                    }

                    if ("Y".equalsIgnoreCase(toggle)) {
                        statusColStart = otResultCol + 1;
                        headerRow.createCell(statusColStart).setCellValue("OS Status");
                        headerRow.createCell(statusColStart + 1).setCellValue("OT Status");
                    } else {
                        statusColStart = osResultCol + 1;
                        headerRow.createCell(statusColStart).setCellValue(currentEngine + " Status");
                    }
                    System.out.println("Debug: Status columns added, statusColStart=" + statusColStart);
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

                    // Compare the results
                    ComparisonResult comparison = compareJsonResults(osResult, otResult);

                    // Write results to new columns
                    row.createCell(comparisonCol).setCellValue(comparison.isIdentical ? "Identical" : "Different");

                    // Apply color formatting
                    Cell comparisonCell = row.getCell(comparisonCol);
                    if (comparison.isIdentical) {
                        // Green for identical
                        org.apache.poi.ss.usermodel.CellStyle style = workbook.createCellStyle();
                        org.apache.poi.xssf.usermodel.XSSFCellStyle xssfStyle = (org.apache.poi.xssf.usermodel.XSSFCellStyle) style;
                        byte[] green = new byte[]{(byte)144, (byte)238, (byte)144};
                        xssfStyle.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(green, null));
                        xssfStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                        comparisonCell.setCellStyle(style);
                    } else {
                        // Red for different
                        org.apache.poi.ss.usermodel.CellStyle style = workbook.createCellStyle();
                        org.apache.poi.xssf.usermodel.XSSFCellStyle xssfStyle = (org.apache.poi.xssf.usermodel.XSSFCellStyle) style;
                        byte[] red = new byte[]{(byte)255, (byte)182, (byte)193};
                        xssfStyle.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(red, null));
                        xssfStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                        comparisonCell.setCellStyle(style);
                    }

                    row.createCell(diffPercentCol).setCellValue(comparison.differencePercent + "%");
                    row.createCell(summaryCol).setCellValue(comparison.summary);

                    // Add status if analyzer=Y
                    if ("Y".equalsIgnoreCase(props.getProperty("analyzer"))) {
                        if (headersFound) {
                            String sourceInput = row.getCell(sourceInputCol).getStringCellValue();
                            String targetInput = row.getCell(targetInputCol).getStringCellValue();
                            String targetColumn = row.getCell(targetColumnCol).getStringCellValue();
                            String watchlist = row.getCell(watchlistCol).getStringCellValue();
                            String n_uid = row.getCell(nuidCol).getStringCellValue();
                            if ("Y".equalsIgnoreCase(toggle)) {
                                boolean osPass = checkMatch(osResult, watchlist, n_uid, targetColumn, "OS");
                                boolean otPass = checkMatch(otResult, watchlist, n_uid, targetColumn, "OT");
                                Cell osCell = row.createCell(statusColStart);
                                setPassFail(osCell, osPass, workbook);
                                Cell otCell = row.createCell(statusColStart + 1);
                                setPassFail(otCell, otPass, workbook);
                            } else {
                                boolean pass = checkMatch(osResult, watchlist, n_uid, targetColumn, "OS");
                                Cell cell = row.createCell(statusColStart);
                                setPassFail(cell, pass, workbook);
                            }
                        } else {
                            // Default to Fail
                            if ("Y".equalsIgnoreCase(toggle)) {
                                Cell osCell = row.createCell(statusColStart);
                                setPassFail(osCell, false, workbook);
                                Cell otCell = row.createCell(statusColStart + 1);
                                setPassFail(otCell, false, workbook);
                            } else {
                                Cell cell = row.createCell(statusColStart);
                                setPassFail(cell, false, workbook);
                            }
                        }
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

    private static ComparisonResult compareJsonResults(String osResult, String otResult) {
        ComparisonResult result = new ComparisonResult();

        if (osResult == null || osResult.isEmpty() || otResult == null || otResult.isEmpty()) {
            result.isIdentical = false;
            result.differencePercent = 100;
            result.summary = "One or both results are empty";
            return result;
        }

        try {
            // Parse RULESET_RESULT objects (maps keyed by rulesetId)
            org.json.JSONObject osObj = new org.json.JSONObject(osResult);
            org.json.JSONObject otObj = new org.json.JSONObject(otResult);

            // Build maps from rulesetId to matchCount
            java.util.Map<String, Integer> osRulesets = new java.util.HashMap<>();
            java.util.Map<String, Integer> otRulesets = new java.util.HashMap<>();

            // Iterate through OS rulesets
            java.util.Iterator<String> osKeys = osObj.keys();
            while (osKeys.hasNext()) {
                String rulesetId = osKeys.next();
                org.json.JSONObject ruleset = osObj.getJSONObject(rulesetId);
                int matchCount = ruleset.getInt("matchCount");
                osRulesets.put(rulesetId, matchCount);
            }

            // Iterate through OT rulesets
            java.util.Iterator<String> otKeys = otObj.keys();
            while (otKeys.hasNext()) {
                String rulesetId = otKeys.next();
                org.json.JSONObject ruleset = otObj.getJSONObject(rulesetId);
                int matchCount = ruleset.getInt("matchCount");
                otRulesets.put(rulesetId, matchCount);
            }

            // Compare the ruleset maps
            java.util.Set<String> allRulesetIds = new java.util.HashSet<>();
            allRulesetIds.addAll(osRulesets.keySet());
            allRulesetIds.addAll(otRulesets.keySet());

            int totalRulesets = allRulesetIds.size();
            int differentRulesets = 0;
            java.util.List<String> differences = new java.util.ArrayList<>();

            for (String rulesetId : allRulesetIds) {
                Integer osCount = osRulesets.get(rulesetId);
                Integer otCount = otRulesets.get(rulesetId);

                if (osCount == null) {
                    differentRulesets++;
                    differences.add(rulesetId + "(missing in OS)");
                } else if (otCount == null) {
                    differentRulesets++;
                    differences.add(rulesetId + "(missing in OT)");
                } else if (!osCount.equals(otCount)) {
                    differentRulesets++;
                    differences.add(rulesetId + "(" + osCount + "≠" + otCount + ")");
                }
            }

            result.isIdentical = differentRulesets == 0;
            result.differencePercent = totalRulesets > 0 ? (differentRulesets * 100) / totalRulesets : 0;

            if (result.isIdentical) {
                result.summary = "All " + totalRulesets + " rulesets identical";
            } else {
                result.summary = String.join("; ", differences.subList(0, Math.min(differences.size(), 5)));
                if (differences.size() > 5) {
                    result.summary += "; ... and " + (differences.size() - 5) + " more";
                }
            }

        } catch (Exception e) {
            result.isIdentical = false;
            result.differencePercent = 100;
            result.summary = "Error parsing RULESET_RESULTS: " + e.getMessage();
        }

        return result;
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

    private static class ComparisonResult {
        boolean isIdentical;
        int differencePercent;
        String summary;
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
        System.out.println("Debug checkMatch: watchlist=" + watchlist + ", n_uid=" + n_uid + ", targetColumn=" + targetColumn + ", engine=" + engine + ", expectedIndex=" + expectedIndex);
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
                        org.json.JSONArray matchedCols = match.optJSONArray("matchedCols");
                        boolean contains = false;
                        if (matchedCols != null) {
                            for (int k = 0; k < matchedCols.length(); k++) {
                                if (targetColumn.equalsIgnoreCase(matchedCols.getString(k))) {
                                    contains = true;
                                    break;
                                }
                            }
                        }
                        System.out.println("Checking match object: indexName=" + indexName + ", matchNuid=" + matchNuid + ", matchedCols=" + (matchedCols != null ? matchedCols.toString() : "null") + ", contains=" + contains);
                        if (expectedIndex.equals(indexName) && n_uid.equals(matchNuid) && contains) {
                            System.out.println("Match found for " + watchlist + " in " + engine);
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking match: " + e.getMessage());
        }
        System.out.println("No match found for " + watchlist + " in " + engine);
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
