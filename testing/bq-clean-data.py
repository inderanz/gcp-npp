import time
import uuid
import json
from datetime import datetime, timezone, timedelta
from google.cloud import spanner, bigquery
from threading import Thread

# Configuration
SPANNER_INSTANCE_ID = "sample-instance"
SPANNER_DATABASE_ID = "audit-db"
SPANNER_TABLE_NAME = "payment_audit_trail"
BQ_PROJECT_ID = "spanner-gke-443910"
BQ_DATASET_ID = "audit_service_dataset"
BQ_LOG_TABLE = "showcase_log"
POLL_INTERVAL = 5  # Poll interval for BigQuery script

# Initialize Spanner and BigQuery clients
spanner_client = spanner.Client()
bigquery_client = bigquery.Client()


def log_to_bigquery(source, details):
    """
    Log events to the BigQuery showcase_log table.
    """
    table_id = f"{BQ_PROJECT_ID}.{BQ_DATASET_ID}.{BQ_LOG_TABLE}"
    rows_to_insert = [
        {
            "log_time": datetime.now(timezone.utc).isoformat(),
            "source": source,
            "log_details": json.dumps(details),  # Ensure all details are serialized to JSON
        }
    ]
    try:
        bigquery_client.insert_rows_json(table_id, rows_to_insert)
        print(f"[{datetime.now(timezone.utc).isoformat()}] Logged to BigQuery: {source} - {details}")
    except Exception as e:
        print(f"Failed to log to BigQuery: {e}")


def insert_sample_data_to_spanner():
    """
    Continuously inserts sample records into the Spanner table.
    """
    instance = spanner_client.instance(SPANNER_INSTANCE_ID)
    database = instance.database(SPANNER_DATABASE_ID)

    while True:
        # Generate sample data
        sample_data = {
            "PUID": str(uuid.uuid4()),
            "Action": "CREATE_PAYMENT",
            "Status": "SUCCESS",
            "Timestamp": datetime.now(timezone.utc),
            "ServiceName": "PaymentService",
            "Metadata": '{"amount": 100.50, "currency": "USD"}',
            "RetryCount": 0,
            "ErrorDetails": None,
        }

        # Insert data into Spanner
        try:
            with database.batch() as batch:
                batch.insert(
                    table=SPANNER_TABLE_NAME,
                    columns=list(sample_data.keys()),
                    values=[list(sample_data.values())],
                )
            log_to_bigquery("Spanner", sample_data)
            print(f"Inserted record into Spanner: {sample_data}")
        except Exception as e:
            print(f"Failed to insert into Spanner: {e}")
        time.sleep(5)  # Wait before inserting the next record


def fetch_new_data_from_bigquery(last_check_time):
    """
    Fetches new rows from the BigQuery table based on the last_check_time.
    """
    query = f"""
    SELECT log_time, source, log_details
    FROM `{BQ_PROJECT_ID}.{BQ_DATASET_ID}.{BQ_LOG_TABLE}`
    WHERE log_time > @last_check_time
    ORDER BY log_time ASC
    """
    job_config = bigquery.QueryJobConfig(
        query_parameters=[
            bigquery.ScalarQueryParameter("last_check_time", "TIMESTAMP", last_check_time)
        ]
    )
    try:
        query_job = bigquery_client.query(query, job_config=job_config)
        return list(query_job.result())
    except Exception as e:
        print(f"Failed to fetch data from BigQuery: {e}")
        return []


def parse_log_details(log_details):
    """
    Parse the log_details field safely to avoid JSON issues.
    """
    try:
        return json.loads(log_details) if isinstance(log_details, str) else log_details
    except Exception as e:
        print(f"Failed to parse log_details: {e}")
        return log_details  # Return as-is if parsing fails


def monitor_bigquery_table():
    """
    Continuously monitors the BigQuery table for new rows and logs events.
    """
    last_check_time = datetime.now(timezone.utc) - timedelta(seconds=5)

    print("Monitoring BigQuery table for real-time updates...")
    while True:
        print(f"Polling BigQuery at {datetime.now(timezone.utc).isoformat()}...")
        new_rows = fetch_new_data_from_bigquery(last_check_time)

        if new_rows:
            print(f"New records found at {datetime.now(timezone.utc).isoformat()}:")
            for row in new_rows:
                log_details = parse_log_details(row["log_details"])
                print(f"Source: {row['source']}, Log Time: {row['log_time']}, Details: {log_details}")
            last_check_time = max(row["log_time"] for row in new_rows)
        else:
            print(f"No new rows found at {datetime.now(timezone.utc).isoformat()}.")
        time.sleep(POLL_INTERVAL)


def main():
    """
    Main function to start Spanner insertion and BigQuery monitoring concurrently.
    """
    try:
        print("Starting Spanner and BigQuery demonstration...")

        spanner_thread = Thread(target=insert_sample_data_to_spanner)
        bigquery_thread = Thread(target=monitor_bigquery_table)

        # Start threads
        spanner_thread.start()
        bigquery_thread.start()

        # Wait for both threads to complete
        spanner_thread.join()
        bigquery_thread.join()
    except KeyboardInterrupt:
        print("\nProcess terminated by user.")


if __name__ == "__main__":
    main()
