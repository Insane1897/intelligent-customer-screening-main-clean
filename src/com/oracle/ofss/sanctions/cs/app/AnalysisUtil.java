package com.oracle.ofss.sanctions.cs.app;

import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;

public class AnalysisUtil {
    public static int[] categorizeMatchCounts(String resultJson) {
        int[] counts = new int[4]; // [SAN, PEP, EDD, PRB]
        if (resultJson == null || resultJson.isEmpty()) {
            return counts;
        }
        try {
            JSONObject resultObj = new JSONObject(resultJson);
            Iterator<String> keys = resultObj.keys();
            while (keys.hasNext()) {
                String rulesetName = keys.next();
                JSONObject ruleset = resultObj.getJSONObject(rulesetName);
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

    public static int sumMatchCount(JSONObject rulesetObj) {
        int total = 0;
        Iterator<String> keys = rulesetObj.keys();
        while (keys.hasNext()) {
            String rulesetId = keys.next();
            JSONObject ruleset = rulesetObj.getJSONObject(rulesetId);
            total += ruleset.getInt("matchCount");
        }
        return total;
    }

    public static final Map<String, Map<String, String>> wlToIndex = Map.ofEntries(
        Map.entry("EU", Map.of("OS", "idx_european_union", "OT", "FCC_WL_EUROPEAN_UNION_OT")),
        Map.entry("OFAC", Map.of("OS", "idx_ofac", "OT", "FCC_WL_OFAC_OT")),
        Map.entry("UN", Map.of("OS", "idx_un", "OT", "FCC_WL_UN_OT")),
        Map.entry("HMT", Map.of("OS", "idx_hmt", "OT", "FCC_WL_HMT_OT")),
        Map.entry("DJW", Map.of("OS", "idx_djw", "OT", "FCC_WL_DJW_OT")),
        Map.entry("COUNTRY", Map.of("OS", "idx_tf_dim_country", "OT", "FCC_TF_DIM_COUNTRY_OT")),
        Map.entry("WCSTANDARD", Map.of("OS", "idx_wc_standard", "OT", "FCC_WL_WC_STANDARD_OT")),
        Map.entry("WCPREM", Map.of("OS", "idx_wc_premium", "OT", "FCC_WL_WC_PREMIUM_OT")),
        Map.entry("PRV_WL1", Map.of("OS", "idx_privatelist", "OT", "FCC_WL_PRIVATELIST_OT"))
    );

    // Reverse map for index to watchlist
    public static final Map<String, String> indexToWatchlist = Map.ofEntries(
        Map.entry("idx_european_union", "EU"),
        Map.entry("FCC_WL_EUROPEAN_UNION_OT", "EU"),
        Map.entry("idx_ofac", "OFAC"),
        Map.entry("FCC_WL_OFAC_OT", "OFAC"),
        Map.entry("idx_un", "UN"),
        Map.entry("FCC_WL_UN_OT", "UN"),
        Map.entry("idx_hmt", "HMT"),
        Map.entry("FCC_WL_HMT_OT", "HMT"),
        Map.entry("idx_djw", "DJW"),
        Map.entry("FCC_WL_DJW_OT", "DJW"),
        Map.entry("idx_tf_dim_country", "COUNTRY"),
        Map.entry("FCC_TF_DIM_COUNTRY_OT", "COUNTRY"),
        Map.entry("idx_wc_standard", "WCSTANDARD"),
        Map.entry("FCC_WL_WC_STANDARD_OT", "WCSTANDARD"),
        Map.entry("idx_wc_premium", "WCPREM"),
        Map.entry("FCC_WL_WC_PREMIUM_OT", "WCPREM"),
        Map.entry("idx_privatelist", "PRV_WL1"),
        Map.entry("FCC_WL_PRIVATELIST_OT", "PRV_WL1")
    );

    public static boolean checkMatch(String resultJson, String watchlist, String n_uid, String targetColumn, String engine) {
        if (resultJson == null || resultJson.isEmpty()) return false;
        Map<String, String> indexMap = wlToIndex.get(watchlist);
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
        Set<String> requiredColumns = new HashSet<>();
        if (targetColumn != null && !targetColumn.isEmpty()) {
            String[] columns = targetColumn.split(";");
            for (String col : columns) {
                requiredColumns.add(col.trim().toUpperCase());
            }
        }
        if (requiredColumns.isEmpty()) return true; // No columns to check, treat as Pass

        System.out.println("Debug checkMatch: watchlist=" + watchlist + ", n_uid=" + n_uid + ", targetColumns=" + requiredColumns + ", engine=" + engine + ", expectedIndex=" + expectedIndex);
        try {
            JSONObject obj = new JSONObject(resultJson);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String rulesetId = keys.next();
                JSONObject ruleset = obj.getJSONObject(rulesetId);
                JSONArray matches = ruleset.optJSONArray("matches");
                if (matches != null) {
                    for (int i = 0; i < matches.length(); i++) {
                        JSONObject match = matches.getJSONObject(i);
                        String indexName = match.optString("indexName", "");
                        String matchNuid = match.optString("n_uid", "");

                        // Filter by N_UID and indexName first
                        if (!n_uid.equals(matchNuid) || !expectedIndex.equals(indexName)) {
                            continue;
                        }

                        // Check matchedCols and remove from required set
                        JSONArray matchedCols = match.optJSONArray("matchedCols");
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

    public static class MatchObject {
        String n_uid;
        String watchlist;
        String ruleName;
        List<String> matchedCols;
        String type; // Added type field

        MatchObject(String n_uid, String watchlist, String ruleName, List<String> matchedCols, String type) {
            this.n_uid = n_uid;
            this.watchlist = watchlist;
            this.ruleName = ruleName;
            this.matchedCols = new ArrayList<>(matchedCols);
            Collections.sort(this.matchedCols);
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            MatchObject that = (MatchObject) obj;
            return Objects.equals(n_uid, that.n_uid) &&
                   Objects.equals(watchlist, that.watchlist) &&
                   Objects.equals(ruleName, that.ruleName) &&
                   Objects.equals(matchedCols, that.matchedCols);  // Exact list match
        }

        @Override
        public int hashCode() {
            return Objects.hash(n_uid, watchlist, ruleName, matchedCols);
        }

        @Override
        public String toString() {
            return new JSONObject()
                .put("n_uid", n_uid)
                .put("watchlist", watchlist)
                .put("ruleName", ruleName)
                .put("matchedCols", new JSONArray(matchedCols))
                .toString();
        }
    }

    public static class RulesetMatches {
        Map<String, List<MatchObject>> matchesByType = new HashMap<>();
    }

    public static RulesetMatches parseToMatches(String resultJson, String engine) {
        RulesetMatches rms = new RulesetMatches();
        if (resultJson == null || resultJson.trim().isEmpty() || !resultJson.trim().startsWith("{") || resultJson.contains("[response exceeds")) return rms;
        try {
            JSONObject obj = new JSONObject(resultJson);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String rulesetId = keys.next();
                JSONObject ruleset = obj.getJSONObject(rulesetId);
                JSONArray matches = ruleset.optJSONArray("matches");
                if (matches != null) {
                    for (int i = 0; i < matches.length(); i++) {
                        JSONObject match = matches.getJSONObject(i);
                        String n_uid = match.optString("n_uid", "");
                        String indexName = match.optString("indexName", "");
                        String watchlist = indexToWatchlist.get(indexName);
                        if (watchlist == null) continue; // Skip unknown index
                        String ruleName = match.optString("ruleName", "");
                        JSONArray matchedColsJson = match.optJSONArray("matchedCols");
                        List<String> matchedCols = new ArrayList<>();
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
                        rms.matchesByType.computeIfAbsent(type, k -> new ArrayList<>()).add(new MatchObject(n_uid, watchlist, ruleName, matchedCols, type));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing to matches: " + e.getMessage());
        }
        return rms;
    }
}
