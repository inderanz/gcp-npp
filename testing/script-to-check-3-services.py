import time
import uuid
import json
from datetime import datetime, timezone, timedelta
from google.cloud import spanner, bigquery
from threading import Thread
import signal
import sys

# Configuration
SPANNER_INSTANCE_ID = "sample-instance"
SPANNER_DATABASE_ID = "shared-db"
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
    errors = bigquery_client.insert_rows_json(table_id, rows_to_insert)
    if not errors:
        total_requests += 1  # Increment the counter
        print(f"[{datetime.now(timezone.utc).isoformat()}] Logged to BigQuery: {source} - {details}")
    else:
        print(f"Failed to log to BigQuery: {errors}")

def insert_sample_data_to_spanner():
    """
    Continuously inserts sample records into the Spanner tables.
    """
    instance = spanner_client.instance(SPANNER_INSTANCE_ID)
    database = instance.database(SPANNER_DATABASE_ID)

    while True:
        # Generate sample data for Payments table
        payments_data = {
            "PaymentUID": str(uuid.uuid4()),
            "UserId": f"user-{uuid.uuid4().hex[:8]}",
            "Amount": float(round(100 + uuid.uuid4().int % 1000, 2)),  # Ensure Amount is a float
            "Status": "SUCCESS",
            "Timestamp": datetime.now(timezone.utc),
            "Action": "CREATE_PAYMENT",
            "Metadata": json.dumps({"currency": "USD", "transactionType": "debit"}),  # Serialize to JSON string
            "RetryCount": 0,
            "ErrorDetails": None,
        }

        # Generate sample data for Reconciliation table
        reconciliation_data = {
            "PUID": str(uuid.uuid4()),
            "Amount": float(round(500 + uuid.uuid4().int % 500, 2)),  # Ensure Amount is a float
            "Status": "SUCCESS",
            "Timestamp": datetime.now(timezone.utc),
            "Action": "RECONCILE",
            "Metadata": json.dumps({"currency": "EUR", "source": "system"}),  # Serialize to JSON string
            "RetryCount": 0,
            "ErrorDetails": None,
        }

        # Generate sample data for Transactions table
        transactions_data = {
            "PUID": str(uuid.uuid4()),
            "UserId": f"user-{uuid.uuid4().hex[:8]}",
            "Amount": float(round(200 + uuid.uuid4().int % 800, 2)),  # Ensure Amount is a float
            "Status": "FAILED",
            "DataSource": "InderBank",
            "Timestamp": datetime.now(timezone.utc),
            "Action": "PROCESS_TRANSACTION",
            "Metadata": json.dumps({"currency": "INR", "description": "Failed transaction"}),  # Serialize to JSON string
            "RetryCount": 1,
            "ErrorDetails": "Insufficient funds",
        }

        # Insert data into Spanner
        with database.batch() as batch:
            batch.insert(
                table="Payments",
                columns=list(payments_data.keys()),
                values=[list(payments_data.values())],
            )
            batch.insert(
                table="Reconciliation",
                columns=list(reconciliation_data.keys()),
                values=[list(reconciliation_data.values())],
            )
            batch.insert(
                table="Transactions",
                columns=list(transactions_data.keys()),
                values=[list(transactions_data.values())],
            )

        # Log data insertion to BigQuery
        log_to_bigquery("Spanner", f"Inserted record into Payments: {payments_data}")
        log_to_bigquery("Spanner", f"Inserted record into Reconciliation: {reconciliation_data}")
        log_to_bigquery("Spanner", f"Inserted record into Transactions: {transactions_data}")

        print(f"Inserted record into Payments: {payments_data}")
        print(f"Inserted record into Reconciliation: {reconciliation_data}")
        print(f"Inserted record into Transactions: {transactions_data}")

        time.sleep(5)  # Wait before inserting the next set of records

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
