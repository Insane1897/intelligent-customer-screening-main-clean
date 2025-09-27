# System Patterns

## System Architecture
The Intelligent Customer Screening application follows a modular pipeline architecture:

```
Raw Message Generation → Message Processing → Matching Engine Toggle → Analysis & Reporting
```

### Core Components
- **MainCS**: Entry point that orchestrates the entire pipeline based on configuration
- **RawMessageGeneratorCS**: Generates test/raw customer data for screening
- **MessageProcessingUtilityCS**: Core screening logic against sanctions database
- **ToggleMatchingEngineCS**: Switches between different matching engines (ES/OS)
- **MessageResponseAnalyzerCS**: Analyzes screening results and generates reports
- **SQLUtilityCS**: Handles database operations and queries
- **TransliterationUtil**: Text processing for international character support

## Key Technical Decisions
- **Configuration-Driven Design**: All behavior controlled via properties file for flexibility
- **Modular Pipeline**: Each step can be enabled/disabled independently
- **Database-Centric**: Oracle database as the source of truth for sanctions data
- **Dual Engine Support**: Ability to toggle between Elasticsearch and OpenSearch for matching
- **Output Flexibility**: Support for both human-readable (Excel) and machine-readable (JSON) formats
- **Update-Only Toggle**: Engine toggle updates existing FCC_MR_C_MATCHINGTARGET rows instead of inserting new ones, ensuring data integrity and preventing table bloat
- **Selective Table Updates**: Toggle operations now update only FCC_MR_C_MATCHINGTARGET table, not FCC_CS_JRSDN_ENTITY_PIPELINE_MAP (job names set manually)
- **Common Pipeline Override**: Optional configuration properties allow unified pipeline usage across engines, falling back to engine-specific pipelines when not set

## Design Patterns Used
- **Factory Pattern**: Implicit in engine toggle mechanism for different matching strategies
- **Utility Classes**: Static methods for common operations (SQL, transliteration, analysis)
- **Configuration Pattern**: Properties-based configuration management
- **Pipeline Pattern**: Sequential processing through configurable modules
- **Data Transfer Objects**: SourceInputModelEnt and SourceInputModelInd for customer data

## Component Relationships
```
MainCS
├── RawMessageGeneratorCS (optional)
├── ToggleMatchingEngineCS
├── MessageProcessingUtilityCS
│   ├── SQLUtilityCS
│   └── TransliterationUtil
└── MessageResponseAnalyzerCS
    ├── Excel formatting
    └── JSON output
```

## Critical Implementation Paths
1. **Screening Flow**:
   - Load configuration from properties file
   - Generate/process raw messages
   - Query database with current matching engine
   - Analyze and score results
   - Generate timestamped output files

2. **Engine Toggle Flow**:
   - Identify current active engine
   - Switch to alternate engine
   - Re-run screening with new engine
   - Compare results between engines

3. **Analysis Flow**:
   - Parse screening responses
   - Apply confidence scoring
   - Format results for Excel/JSON output
   - Rename files with timestamps

## Data Flow
- Input: Properties configuration + optional raw message files
- Processing: Customer data → Screening queries → Match results
- Output: Excel reports (.xlsx) + JSON data (.json) with timestamped names

## Error Handling Patterns
- Properties file validation at startup
- Database connection error handling
- File I/O error logging
- Graceful degradation when optional modules fail

## Performance Considerations
- Database query optimization through SQLUtilityCS
- Batch processing for multiple customers
- Minimal memory footprint for command-line operation
- Timed execution logging for performance monitoring
