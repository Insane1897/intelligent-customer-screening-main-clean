# Product Context

## Why This Project Exists
Financial institutions face increasing regulatory pressure to screen customers against sanctions lists to prevent money laundering, terrorism financing, and other financial crimes. Manual screening processes are time-consuming, error-prone, and don't scale well with growing customer volumes. This project automates the screening process while maintaining auditability and compliance standards.

## Problems Solved
- **Manual Screening Bottleneck**: Reduces time spent on manual customer screening from hours to seconds
- **Human Error Reduction**: Consistent, rule-based screening eliminates subjective judgment errors
- **Scalability**: Handles high-volume customer onboarding without proportional staff increases
- **Compliance Documentation**: Automated generation of screening reports for regulatory audits
- **Engine Flexibility**: Ability to compare and switch between different matching algorithms for optimization

## How It Should Work
1. **Input Processing**: Accept customer data in raw message format (individuals and entities)
2. **Sanctions Screening**: Query Oracle database with configurable matching engines (e.g., Elasticsearch vs OpenSearch)
3. **Result Analysis**: Compare screening outcomes, identify matches, and score confidence levels
4. **Report Generation**: Output structured Excel and JSON reports with timestamps and processing metadata
5. **Engine Toggle**: Support switching between matching engines for A/B testing and performance comparison

## User Experience Goals
- **Simple Configuration**: Properties-file driven setup requiring minimal technical expertise
- **Clear Outputs**: Human-readable Excel reports with color-coded results and detailed JSON for programmatic consumption
- **Fast Execution**: Sub-10 second processing times for typical customer volumes
- **Reliable Operation**: Graceful error handling with clear logging for troubleshooting
- **Audit Trail**: Complete traceability of screening decisions and processing steps

## Target Users
- **Compliance Officers**: Primary users who configure and monitor screening processes
- **Risk Analysts**: Review screening results and fine-tune matching parameters
- **IT Administrators**: Deploy and maintain the application infrastructure
- **Auditors**: Verify compliance through generated reports and logs

## Business Value
- Reduces operational costs through automation
- Improves compliance accuracy and reduces regulatory risk
- Enables faster customer onboarding without compromising due diligence
- Provides data-driven insights for optimizing screening algorithms
