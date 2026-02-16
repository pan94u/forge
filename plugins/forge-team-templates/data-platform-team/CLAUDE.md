# Data Platform Team

## Team Overview

We build and maintain the data infrastructure: ETL pipelines, data warehousing, real-time streaming, and analytics services. Our systems process millions of events daily and serve data to business intelligence dashboards, machine learning models, and downstream services.

## Tech Stack

- **Languages**: Kotlin (services), Python (notebooks, ML pipelines), SQL (transformations)
- **Batch Processing**: Apache Spark on Kubernetes
- **Stream Processing**: Apache Kafka + Kafka Streams / Apache Flink
- **Data Warehouse**: Snowflake (production), DuckDB (local development)
- **Orchestration**: Apache Airflow for DAG management
- **Data Quality**: Great Expectations for data validation
- **Build**: Gradle Kotlin DSL (Kotlin services), Poetry (Python projects)

## Build & Run

```bash
# Build Kotlin services
./gradlew build

# Run Kafka Streams application
./gradlew :streaming:event-processor:bootRun

# Run Airflow locally
docker-compose -f infrastructure/airflow/docker-compose.yml up

# Run Spark job locally
./gradlew :batch:daily-aggregation:sparkSubmit --args="--date=2026-02-16 --env=local"

# Run data quality checks
cd data-quality && poetry run great_expectations checkpoint run daily_validation

# Run Python tests
cd pipelines && poetry run pytest

# Run Kotlin tests
./gradlew test
```

## Coding Conventions

### Pipeline Design
- Every pipeline must be idempotent (safe to re-run)
- Use exactly-once semantics for stream processing where possible
- Partition data by date for efficient querying and backfill
- Schema evolution must be backward compatible (use Avro or Protobuf with schema registry)

### Code Structure (Kotlin Services)
- **Processor**: Stream processing logic. One processor per domain event type
- **Transformer**: Data transformation functions. Pure functions, no side effects
- **Sink**: Output writers (database, S3, Kafka topic). Handles retries and batching
- **Schema**: Avro/Protobuf schema definitions with version tracking

### Code Structure (Python Pipelines)
- **DAGs**: Airflow DAGs in `dags/` directory. One file per pipeline
- **Tasks**: Individual task implementations in `tasks/`. Thin wrappers around core logic
- **Core**: Business logic in `core/`. Framework-agnostic, testable
- **Models**: Data models in `models/`. Use Pydantic for validation

### Data Quality
- Every pipeline output must have Great Expectations validation
- Critical tables require freshness checks (data not older than SLA)
- Row count anomaly detection on all high-volume tables
- Schema drift detection on all external data sources

### Naming
- Pipeline names: `{frequency}_{domain}_{action}` (e.g., `daily_orders_aggregate`)
- Kafka topics: `{domain}.{entity}.{version}` (e.g., `orders.order-created.v1`)
- Database tables: `{schema}.{entity}_{suffix}` (e.g., `analytics.daily_order_summary`)
- Airflow DAGs: `{team}_{pipeline_name}` (e.g., `data_daily_orders_aggregate`)

### Testing
- Unit tests for all transformers and core logic
- Integration tests with embedded Kafka and Testcontainers
- Data quality tests as part of the pipeline (not separate)
- Backfill tests: verify pipeline produces correct results for historical data

## Security Rules

- NEVER hardcode database credentials. Use Airflow Connections or environment variables
- Production data access requires approved service account
- PII data must be masked or encrypted at rest
- Log only metadata, never raw data contents
- Data retention policies must be enforced programmatically

## Active Forge Plugins

- forge-foundation
- forge-superagent
- forge-knowledge
- forge-deployment
