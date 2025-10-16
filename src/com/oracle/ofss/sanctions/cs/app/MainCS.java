package com.oracle.ofss.sanctions.cs.app;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

public class MainCS {
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(ConstantsCS.CONFIG_FILE_PATH)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("Error reading properties file: " + e.getMessage());
            throw e;
        }

        long startTime = System.currentTimeMillis();
        if (props.getProperty(ConstantsCS.MODULE_RAW_MSG_GENERATOR).equalsIgnoreCase("Y"))
            RawMessageGeneratorCS.generateRawMessage();

        ToggleMatchingEngineCS toggleMatchingEngine = new ToggleMatchingEngineCS();

        if (props.getProperty(ConstantsCS.MODULE_RAW_MSG_PROCESSOR).equalsIgnoreCase("Y")) {
            String currentMatchingEngine = toggleMatchingEngine.findCurrentMatchingEngine();
            System.out.println("Current Matching Engine::: " + currentMatchingEngine);
            MessageProcessingUtilityCS.screenRawMsg(currentMatchingEngine);
            if ("Y".equalsIgnoreCase(props.getProperty(ConstantsCS.ANALYZER))) {
                MessageResponseAnalyzerCS.performAnalysis(currentMatchingEngine);
            }
        }

        if (props.getProperty(ConstantsCS.TOGGLE_MATCHING_ENGINE).equalsIgnoreCase("Y")) {
            String newEsOs = toggleMatchingEngine.toggleMatchingEngine();
            System.out.println("Matching engine set to ::: " + newEsOs);

            if (props.getProperty(ConstantsCS.MODULE_RAW_MSG_PROCESSOR).equalsIgnoreCase("Y")) {
                MessageProcessingUtilityCS.screenRawMsg(newEsOs);
                if ("Y".equalsIgnoreCase(props.getProperty(ConstantsCS.ANALYZER))) {
                    MessageResponseAnalyzerCS.performAnalysis(newEsOs);
                }
            }

            // Check if analysis is required after toggle
            String analyzeAfterToggle = props.getProperty(ConstantsCS.ANALYZE_AFTER_TOGGLE, "Y");
            if ("Y".equalsIgnoreCase(analyzeAfterToggle)) {
                // In-memory comparison if enabled, else Excel-based
                String analyzeInProcessing = props.getProperty(ConstantsCS.ANALYZE_IN_PROCESSING, "N");
                if ("Y".equalsIgnoreCase(analyzeInProcessing)) {
                    MessageProcessingUtilityCS.compareInMemory();
                } else {
                    MessageResponseAnalyzerCS.compareResultsAndUpdateExcel();
                }
            }
        }

        // Format Excel at the end
        MessageResponseAnalyzerCS.formatExcel();

        // Renaming output files
        SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss");
        Date now = new Date();
        String date = dateFormat.format(now);
        String time = timeFormat.format(now);
        String candidateType = props.getProperty(ConstantsCS.CANDIDATE_TYPE);
        String baseName = (candidateType != null ? candidateType : "") + "_" + date + "_" + time;

        File[] filesToRename = {
                ConstantsCS.OUTPUT_XLSX_FILE_PATH,
                ConstantsCS.OUTPUT_JSON_FILE_PATH
        };
        String[] extensions = {".xlsx", ".json"};

        for (int i = 0; i < filesToRename.length; i++) {
            File original = filesToRename[i];
            if (!original.exists()) continue;

            String fileName = baseName + extensions[i];
            File newFile = new File(ConstantsCS.OUTPUT_FOLDER, fileName);
            if (original.renameTo(newFile)) {
                System.out.println("Renamed " + original.getName() + " to " + newFile.getName());
            } else {
                System.err.println("Failed to rename " + original.getName());
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println("\n==========================================================");
        System.out.println("Total time taken by utility: " + (endTime - startTime) / 1000L + " seconds");
        System.out.println("=========================================================");

    }
}
