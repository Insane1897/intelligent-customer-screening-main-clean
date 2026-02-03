# Active Context

## Current Work Focus
Enhanced Excel reporting for sanctions screening results with detailed match data inclusion.

## Recent Changes
- Added cURL execution: posting.useCurl=Y, posting.curlPath=curl
- Dynamic cURL commands with exact headers matching working Postman/cURL setup
- cURL ProcessBuilder for POST/GET with -w %{http_code} for accurate status codes
- GET polling preserved with cURL commands
- HttpClient kept as fallback (useCurl=N)
- All previous fixes maintained: header matching, cookie, polling, timeouts
- Modified JSON truncation filename: Changed from appending seqId to requestId for better traceability in oversized response and ruleset files (e.g., OT_response_full_{requestId}.json instead of OT_response_full_{seqId}.json)
- Enhanced Excel output for detailed match analysis:
  - Extended RULESET_RESULTS (OS/OT) JSON with matchCols array containing: searchString, searchStringTrans, colName, colValue, colValueTrans, searchType, score
  - Enriched comparison columns (Common Matches, OS Missing, Additional Matches in OT) with full matchCols details in MatchObject serialization, now including finalScore
  - Added two new columns: OS COMMON MATCHES and OT COMMON MATCHES, containing common matches with engine-specific data (including finalScore)
  - Updated AnalysisUtil.MatchObject to include MatchCol list with 7 fields and finalScore; enhanced parsing from DB JSON
  - Maintained backward compatibility and truncation handling for oversized JSONs

## Next Steps
- Test enhanced Excel output - verify new columns populate with detailed match data
- Run OS/OT screening to confirm RULESET_RESULTS and comparison sections include enriched JSON
- Monitor for any performance impact from additional JSON processing
- If successful, document as improvement for compliance reporting

## Active Decisions and Considerations
- Project uses Java with Oracle JDBC for database connectivity
- Supports multiple matching engines (Elasticsearch/OpenSearch) with toggle capability
- Output formats include Excel (.xlsx) and JSON for reporting

## Important Patterns and Preferences
- Package structure: com.oracle.ofss.sanctions.cs.app
- Configuration-driven via properties files
- Command-line interface with timed execution logging
- External libraries managed in dedicated folders

## Learnings and Project Insights
- Application follows a modular design with separate utilities for message generation, processing, and analysis
- Database integration is central to screening functionality
- File I/O operations for input/output are timestamped and renamed automatically
