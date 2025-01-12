"""
Payment Processing Simulation Script
====================================

Overview:
---------
This script simulates real-time payment processing by inserting sample data into Google Cloud Spanner
and monitoring BigQuery for changes. It logs new events to a BigQuery table and allows for easy tracking
of transaction data in both Spanner and BigQuery.

Features:
---------
1. **Real-time Data Insertion into Spanner**:
   - Continuously inserts sample payment data into a Spanner table (`payment_audit_trail_psp`).
   - Each record contains unique identifiers such as **PUID**, **TransactionId**, **UserId** and additional metadata fields.
   
2. **Log Data Insertion Events to BigQuery**:
   - Logs each Spanner insertion event into a BigQuery table (`showcase_log`).
   - Each log includes relevant information such as **PUID**, **TransactionId**, **Action**, **Timestamp**, etc.

3. **Continuous Monitoring of BigQuery Table**:
   - The script queries the BigQuery table (`payment_audit_trail_changelog`) to fetch new rows based on **Timestamp**.
   - The fetched rows are logged into BigQuery with a new **log_time**.

4. **Concurrent Execution of Spanner and BigQuery Operations**:
   - The script runs two tasks concurrently using Python threads:
     - One thread for inserting data into Spanner.
     - One thread for monitoring and logging data from BigQuery.

5. **Signal Handling for Graceful Termination**:
   - The script supports graceful termination via **Ctrl+C**.
   - It will exit cleanly and print the total number of requests raised during the execution.

6. **Polling Interval**:
   - The **polling interval** for monitoring BigQuery is set to 5 seconds (`POLL_INTERVAL`).
   - This controls how often the script checks for new data in BigQuery.

7. **Timestamps**:
   - The script uses **timestamps** to manage the timing of data insertions and fetches.
   - It also uses timestamps to track when new events are logged into BigQuery or when data is inserted into Spanner.

Use Cases:
----------
- **Real-time Monitoring and Logging** of Spanner data and BigQuery events.
- **Automated Payment Processing Simulations**.
- **Data Synchronization** between Spanner and BigQuery for downstream processing, analytics, or auditing purposes.

Limitations:
------------
- The script runs indefinitely until stopped manually.
- The polling interval is fixed at **5 seconds**, which may not be suitable for high-frequency updates.

"""
import time
import uuid
from datetime import datetime, timezone, timedelta
from google.cloud import spanner, bigquery
from threading import Thread
import signal
import sys

# Configuration
SPANNER_INSTANCE_ID = "sample-instance"
SPANNER_DATABASE_ID = "audit-db"
SPANNER_TABLE_NAME = "payment_audit_trail_psp"  # Updated table name to reflect changes
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
        # Generate sample data with newly added columns
        sample_data = {
            "PUID": str(uuid.uuid4()),
            "Action": "CREATE_PAYMENT",
            "Status": "SUCCESS",
            "Timestamp": datetime.now(timezone.utc),
            "ServiceName": "PaymentService",
            "Metadata": '{"amount": 100.50, "currency": "USD"}',
            "RetryCount": 0,
            "ErrorDetails": None,
            "UserId": str(uuid.uuid4()),  # New column: UserId
            "Source": "MobileApp",  # New column: Source
            "TransactionId": str(uuid.uuid4()),  # New column: TransactionId
            "Processed": True,  # New column: Processed
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
