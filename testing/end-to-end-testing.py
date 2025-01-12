import time
import uuid
from datetime import datetime, timezone, timedelta
from google.cloud import spanner, bigquery
from threading import Thread
import signal
import sys

# Configuration
SPANNER_INSTANCE_ID = "sample-instance"
SPANNER_DATABASE_ID = "shared-db"
SPANNER_TABLE_NAME = "test_table"
BQ_PROJECT_ID = "spanner-gke-443910"
BQ_DATASET_ID = "audit_service_dataset"
BQ_LOG_TABLE = "showcase_log"
POLL_INTERVAL = 5  # Poll interval for BigQuery script

# Initialize Spanner and BigQuery clients
spanner_client = spanner.Client()
bigquery_client = bigquery.Client()

# Counter to track total requests
total_requests = 0

# Define signal handler for forcefully stopping the script
def signal_handler(sig, frame):
    print(f"\nProcess terminated by user. Total requests raised: {total_requests}")
    sys.exit(0)

# Attach signal handler to capture termination (Ctrl+C)
signal.signal(signal.SIGINT, signal_handler)

def log_to_bigquery(source, details):
    """
    Log events to the BigQuery showcase_log table.
    """
    global total_requests  # Access global variable
    table_id = f"{BQ_PROJECT_ID}.{BQ_DATASET_ID}.{BQ_LOG_TABLE}"
    rows_to_insert = [
        {
            "log_time": datetime.now(timezone.utc).isoformat(),
            "source": source,
            "log_details": details,
        }
    ]
    bigquery_client.insert_rows_json(table_id, rows_to_insert)
    total_requests += 1  # Increment the counter
    print(f"[{datetime.now(timezone.utc).isoformat()}] Logged to BigQuery: {source} - {details}")


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
            "ServiceName": "Test-service",
            "Metadata": '{"amount": 100.50, "currency": "USD"}',
            "RetryCount": 0,
            "ErrorDetails": None,
        }

        # Insert data into Spanner
        with database.batch() as batch:
            batch.insert(
                table=SPANNER_TABLE_NAME,
                columns=list(sample_data.keys()),
                values=[list(sample_data.values())],
            )
        log_to_bigquery("Spanner", f"Inserted record: {sample_data}")
        print(f"Inserted record into Spanner: {sample_data}")
        time.sleep(5)  # Wait before inserting the next record


def fetch_new_data_from_bigquery(last_check_time):
    """
    Fetches new rows from the BigQuery table based on the last_check_time.
    """
    query = f"""
    SELECT *
    FROM `{BQ_PROJECT_ID}.{BQ_DATASET_ID}.payment_audit_trail_changelog`
    WHERE Timestamp > @last_check_time
    ORDER BY Timestamp ASC
    """
    job_config = bigquery.QueryJobConfig(
        query_parameters=[
            bigquery.ScalarQueryParameter("last_check_time", "TIMESTAMP", last_check_time)
        ]
    )
    query_job = bigquery_client.query(query, job_config=job_config)
    rows = list(query_job.result())
    return rows


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
            for row in new_rows:
                log_to_bigquery("BigQuery", f"New row: {dict(row)}")
                print(f"New row from BigQuery: {dict(row)}")
            last_check_time = max(row.Timestamp for row in new_rows)
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
        print(f"\nTotal requests raised: {total_requests}")
        print("\nProcess terminated by user.")


if __name__ == "__main__":
    main()
