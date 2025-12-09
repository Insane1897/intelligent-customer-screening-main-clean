# Active Context

## Current Work Focus
Implemented cURL-based HTTP requests to bypass HttpClient connect issues.

## Recent Changes
- Added cURL execution: posting.useCurl=Y, posting.curlPath=curl
- Dynamic cURL commands with exact headers matching working Postman/cURL setup
- cURL ProcessBuilder for POST/GET with -w %{http_code} for accurate status codes
- GET polling preserved with cURL commands
- HttpClient kept as fallback (useCurl=N)
- All previous fixes maintained: header matching, cookie, polling, timeouts
- Modified JSON truncation filename: Changed from appending seqId to requestId for better traceability in oversized response and ruleset files (e.g., OT_response_full_{requestId}.json instead of OT_response_full_{seqId}.json)

## Next Steps
- Test cURL implementation - should connect successfully using system proxy/SSL
- Monitor logs for cURL command execution and status codes
- If cURL works, document as solution for Java network issues

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
