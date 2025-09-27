# Tech Context

## Technologies Used
- **Language**: Java (JDK 17+ required for ojdbc17)
- **Build System**: Maven (pom.xml files in External Libraries subprojects)
- **Database**: Oracle Database (via JDBC driver ojdbc17.jar)
- **Search Engines**: Elasticsearch and OpenSearch (configurable matching engines)
- **NLP Processing**: Stanford CoreNLP (stanford-corenlp-4.5.9.jar)
- **Excel Processing**: Apache POI (poi-4.1.2.jar, poi-ooxml-4.1.2.jar)
- **JSON Processing**: Jackson (jackson-core-2.11.3.jar, jackson-databind-2.11.3.jar)
- **CSV Processing**: OpenCSV (opencsv-5.9.jar)
- **Text Utilities**: Apache Commons Text (commons-text-1.6.jar, commons-text-1.9.jar)
- **Collections**: Apache Commons Collections (commons-collections4-4.4.jar)
- **I/O Operations**: Apache Commons IO (commons-io-2.8.0.jar)
- **Database Connection Pooling**: Apache Commons DBCP (commons-dbcp2-2.7.0.jar)
- **Logging**: SLF4J (slf4j-api-1.7.30.jar) - note: no implementation jar visible
- **Testing**: JUnit 5 (junit-jupiter-api-5.10.0.jar), Mockito (mockito-core-4.11.0.jar, mockito-core-5.3.1.jar)
- **Internationalization**: ICU4J (icu4j-75.1.jar) for Unicode support

## Development Setup
- **IDE**: Compatible with Eclipse/IntelliJ (based on Maven structure)
- **Source Structure**: Standard Maven layout (src/main/java, src/test/java)
- **Dependencies**: External JARs stored in dedicated folders rather than Maven repository
- **Configuration**: Properties file for runtime behavior (path defined in ConstantsCS.java)
- **Build Output**: Compiled classes in target/ directories of subprojects

## Technical Constraints
- **JDBC Version**: Must use Oracle JDBC 17+ for Java 8+ compatibility
- **Database Access**: Requires active Oracle database connection for screening operations
- **Search Engine**: Needs running Elasticsearch or OpenSearch instance for matching
- **File Permissions**: Write access to output directory for Excel/JSON generation
- **Memory**: Sufficient heap space for large sanctions datasets and NLP processing

## Dependencies Management
- **External Libraries Structure**: JARs organized by category (JUnit Mockito, ojdbc17-full, TFCS Libs)
- **Version Management**: Specific versions pinned for compatibility (e.g., Stanford CoreNLP 4.5.9)
- **Licensing**: Mix of Oracle (ojdbc), Apache 2.0 (Commons, POI), and GPL (Stanford CoreNLP)
- **Classpath**: All JARs must be available at runtime (no Maven dependency resolution)

## Tool Usage Patterns
- **Database Operations**: SQLUtilityCS handles connection management and query execution
- **Text Processing**: TransliterationUtil for international character handling
- **File I/O**: Direct Java File API with timestamp-based naming
- **Configuration**: Properties-based with ConstantsCS for key definitions
- **Testing**: JUnit + Mockito for unit testing (infrastructure visible but tests not yet examined)
- **Build**: Maven for compilation and packaging of utility classes

## Runtime Prerequisites
- **Java Runtime**: JRE/JDK 17 minimum
- **Oracle Database**: Accessible instance with sanctions data
- **Search Engine**: Elasticsearch or OpenSearch cluster
- **File System**: Writable directory for output files
- **Properties File**: Valid configuration file at ConstantsCS.CONFIG_FILE_PATH

## Development Environment Notes
- **OS Compatibility**: Windows (current environment) with cmd.exe shell
- **Source Control**: Git repository with remote origin
- **External Tools**: SQLcl available for database operations (via MCP server)
- **Library Updates**: Manual JAR management requires careful version compatibility testing
