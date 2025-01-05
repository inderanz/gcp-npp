from google.cloud import bigquery
from google.cloud import bigquery_storage
import time
from datetime import datetime, timedelta

# Initialize clients
bq_client = bigquery.Client()
bq_storage_client = bigquery_storage.BigQueryReadClient()

# Define the table
project_id = "spanner-gke-443910"
dataset_id = "audit_service_dataset"
table_id = "payment_audit_trail_changelog"

# Keep track of the last fetch timestamp
last_check = datetime.utcnow() - timedelta(minutes=1)

while True:
    query = f"""
        SELECT *
        FROM `{project_id}.{dataset_id}.{table_id}`
        WHERE _metadata_big_query_commit_timestamp > TIMESTAMP("{last_check.isoformat()}")
        ORDER BY _metadata_big_query_commit_timestamp DESC
    """
    
    try:
        # Run query
        query_job = bq_client.query(query)
        rows = list(query_job.result())

        # Print the fetched rows
        for row in rows:
            print(row)
        
        # Update the last check timestamp
        last_check = datetime.utcnow()

    except Exception as e:
        print(f"Error fetching data: {e}")
    
    # Short delay to avoid overwhelming BigQuery API
    time.sleep(2)  # Adjust interval as needed
