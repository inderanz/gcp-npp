import time
from google.cloud import bigquery
from datetime import datetime, timedelta

# Configuration
PROJECT_ID = "spanner-gke-443910"
DATASET_ID = "audit_service_dataset"
TABLE_ID = "payment_audit_trail_changelog"
POLL_INTERVAL = 5  # Time in seconds between queries

def fetch_new_data(client, last_check_time):
    """
    Fetches new rows from the BigQuery table based on the last_check_time.
    """
    query = f"""
    SELECT *
    FROM `{PROJECT_ID}.{DATASET_ID}.{TABLE_ID}`
    WHERE Timestamp > @last_check_time
    ORDER BY Timestamp ASC
    """
    job_config = bigquery.QueryJobConfig(
        query_parameters=[
            bigquery.ScalarQueryParameter("last_check_time", "TIMESTAMP", last_check_time)
        ]
    )

    query_job = client.query(query, job_config=job_config)
    rows = list(query_job.result())
    return rows

def main():
    client = bigquery.Client()
    last_check_time = datetime.utcnow() - timedelta(seconds=12)  # Start 12 seconds before the script starts

    print("Listening for real-time updates in the BigQuery table...")
    try:
        while True:
            new_rows = fetch_new_data(client, last_check_time)

            if new_rows:
                for row in new_rows:
                    print(f"New Row: {dict(row)}")
                # Update the last_check_time to the latest Timestamp
                last_check_time = max(row.Timestamp for row in new_rows)

            time.sleep(POLL_INTERVAL)  # Wait for the next poll
    except KeyboardInterrupt:
        print("\nStopped listening for updates.")

if __name__ == "__main__":
    main()
