# Project Brief

## Project Name
Intelligent Customer Screening (ICS)

## Core Requirements
Develop a real-time customer screening utility for sanctions compliance in financial services. The application processes raw messages, screens against sanctions lists using configurable matching engines, analyzes results, and generates reports in Excel and JSON formats.

## Goals
- Automate sanctions screening to reduce manual effort and improve compliance accuracy
- Support both entity (corporate) and individual customer screening
- Provide configurable matching engines with toggle capabilities
- Generate comprehensive analysis reports for auditing and optimization

## Scope
- Java-based command-line utility
- Integration with Oracle database for sanctions data
- Message processing pipeline: raw message generation → screening → analysis
- Output formats: Excel (.xlsx) and JSON
- Support for transliteration and NLP-based text processing

## Key Deliverables
- Raw message generator module
- Message processing and screening utility
- Matching engine toggle mechanism
- Response analysis and comparison tools
- Automated file naming and formatting

## Success Criteria
- Accurate screening against sanctions lists
- Fast processing times (<10 seconds for typical workloads)
- Configurable via properties file
- Reliable database connectivity
- Clear, auditable output reports

## Constraints
- Must use existing Oracle infrastructure
- Limited to approved open-source libraries
- Command-line interface (no GUI required)
- Windows environment compatibility

This brief serves as the foundation for all other Memory Bank files.
