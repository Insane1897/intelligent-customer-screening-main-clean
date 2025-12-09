# Progress

## What Works
- **Project Structure**: Basic Java application structure established
- **Core Classes**: Main entry point and utility classes implemented
- **Configuration System**: Properties-based configuration loading
- **Database Integration**: Oracle JDBC connectivity via SQLUtilityCS
- **File I/O**: Basic input/output file handling with timestamping
- **External Libraries**: Required dependencies organized and available
- **Toggle Logic**: Modified to update existing FCC_MR_C_MATCHINGTARGET rows instead of inserting new ones
- **Pipeline Map Updates**: Commented out during toggle operations - job names now set manually
- **Common Pipeline Support**: Added optional common pipeline properties for unified ruleset resolution across engines
- **Multi-Token Raw Messages**: Extended generation to include full name in cartesian product, generating exact messages for all semicolon combinations across all tokens, with variations only on full name
- **Token Filtering**: Refined to only populate Source/Target Input and Target Column with tokens actually present in JSON templates, preventing unused config tokens from being included
- **Configurable Multi-Token Variations**: Added variationtoken and combinevariations properties for flexible variation application (independent or combined across multiple tokens)
- **High-Volume Async Processing**: Optimized MessageProcessingUtilityCS with async HttpClient, configurable concurrency (100 threads, 50 concurrent), exponential backoff retries, and progress monitoring for 10k+ message processing
- **Connectivity Fixes**: Matched headers to working cURL: posting.ofsRemoteUser=appuser, posting.cookieEnvId=vtltbj-prd, removed mismatched headers, increased connectTimeout to 60s
- **GET Polling Implementation**: Removed ?reqId, added posting.getPollAttempts=10 and posting.getPollDelaySeconds=5, polls GET with 5s delays to handle 25s API processing time, increased requestTimeoutSeconds to 60s
- **cURL HTTP Implementation**: Added posting.useCurl=Y, posting.curlPath=curl, executes cURL commands via ProcessBuilder with exact headers, -w for status codes, bypasses HttpClient network issues
- **JSON Truncation Naming**: Modified oversized JSON file naming to use requestId instead of seqId for improved traceability (e.g., {engine}_response_full_{requestId}.json and {engine}_full_{requestId}.json)

## What's Left to Build
- **Complete Testing**: Unit tests for all utility classes
- **Documentation**: Code documentation and user manuals
- **Configuration Validation**: Robust properties file validation
- **Error Handling**: Comprehensive exception handling and recovery
- **Performance Optimization**: Query optimization and caching strategies
- **Integration Testing**: End-to-end pipeline testing with real data

## Current Status
- **Phase**: Initialization Complete
- **Readiness**: Core functionality implemented, ready for testing
- **Blockers**: None identified at this time

## Known Issues
- **SLF4J Implementation**: Logging interface present but no concrete implementation JAR visible
- **Configuration Path**: Hardcoded config file path may need environment flexibility
- **Database Dependencies**: Requires active Oracle instance for full testing
- **Search Engine Integration**: Needs running ES/OS for complete functionality testing

## Evolution of Project Decisions

### Initial Architecture Decision (Pipeline Design)
**Decision**: Implement modular pipeline architecture with configurable modules
**Rationale**: Allows flexible deployment and testing of individual components
**Impact**: Easier maintenance and feature toggling, but adds complexity in orchestration

### Technology Choices (Java + Oracle)
**Decision**: Use Java with Oracle JDBC for database operations
**Rationale**: Leverages existing enterprise infrastructure and developer familiarity
**Impact**: Strong integration capabilities but platform-specific dependencies

### Output Format Decision (Excel + JSON)
**Decision**: Support both Excel and JSON output formats
**Rationale**: Excel for human consumption, JSON for programmatic integration
**Impact**: Dual format support increases complexity but improves usability

### Engine Toggle Feature
**Decision**: Implement ability to switch between Elasticsearch and OpenSearch
**Rationale**: Enables A/B testing and performance comparison of matching algorithms
**Impact**: Adds operational flexibility but requires maintaining dual engine support

### External Library Management
**Decision**: Store JARs in dedicated folders rather than Maven repository
**Rationale**: Simplifies deployment in constrained environments
**Impact**: Manual dependency management but avoids repository access issues
