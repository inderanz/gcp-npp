"""
# Payment Processing Simulation Script

## Overview
This script simulates a payment processing system by updating a payment's status at each stage in a structured JSON format (messagePayload). The script works with **Google Cloud Spanner** to store the payment data and **Google BigQuery** for real-time logging and monitoring of the payment updates.

### Key Features:
1. **Simulates Payment Stages**:
   - The script simulates a payment progressing through 6 stages:
     - Stage 1: Payment Initialization
     - Stage 2: Payment in Progress
     - Stage 3: Fraud Check Completed
     - Stage 4: Risk Assessment Completed
     - Stage 5: Payment Completed
     - Stage 6: Payment Finalization
   - At each stage, the `messagePayload` is updated to reflect the current status of the payment (as a JSON object).

2. **Data Insertion and Updates to Spanner**:
   - Each payment is identified by a unique **PUID** and is inserted into the **Spanner table (`PaymentMilestoneEvents`)**.
   - The `messagePayload` column is updated dynamically at each payment stage, simulating the flow of the payment.

3. **Real-Time Logging to BigQuery**:
   - After every update to the payment data in Spanner, the change is logged in a **BigQuery table (`showcase_log`)** for tracking and audit purposes.
   - The script continuously monitors BigQuery for new changes, providing real-time logging of Spanner updates.

4. **Threaded Execution**:
   - **Two threads** run concurrently:
     - **Spanner thread**: Inserts and updates payment data into Spanner.
     - **BigQuery thread**: Monitors BigQuery for new updates and logs events.

5. **Signal Handling**:
   - Gracefully handles interruptions (like `Ctrl+C`), logs the total number of requests raised, and then terminates the script.

6. **Customizable Delay**:
   - The script includes a customizable polling interval to simulate a delay between payment stages, allowing you to observe the data flow in Spanner and BigQuery.

### Workflow:
1. The script begins by generating a unique **PUID** for the payment and inserts the initial data into Spanner.
2. It then simulates the payment stages, updating the `messagePayload` at each step. The data is updated in Spanner after each stage.
3. The script logs every change to BigQuery to track real-time changes.
4. The monitoring of BigQuery allows you to verify the insertion of new rows corresponding to each payment update.
5. The script continues running, logging updates and simulating further stages until terminated.

### Usage:
1. Run the script and it will start simulating the payment stages for a payment.
2. Monitor both Spanner and BigQuery to see how the data is updated as the payment progresses.
3. Use `Ctrl+C` to stop the script, which will log the total requests raised during execution.

"""
import time
import uuid
import hashlib
import json
from datetime import datetime, timezone, timedelta
from google.cloud import spanner, bigquery
from threading import Thread
import signal
import sys
import logging

# Configuration
SPANNER_INSTANCE_ID = "sample-instance "
SPANNER_DATABASE_ID = "shared-db"
SPANNER_TABLE_NAME = "PaymentMilestoneEvents"  # Spanner table with added 'puid' column
BQ_PROJECT_ID = "spanner-gke-443910"
BQ_DATASET_ID = "audit_service_dataset"
BQ_LOG_TABLE = "showcase_log"
POLL_INTERVAL = 5  # Poll interval for BigQuery script

# Initialize Spanner and BigQuery clients
spanner_client = spanner.Client()
bigquery_client = bigquery.Client()

# Logging configuration
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger()

# Counter to track total requests
total_requests = 0

# Define signal handler for forcefully stopping the script
def signal_handler(sig, frame):
    print(f"\nProcess terminated by user. Total requests raised: {total_requests}")
    sys.exit(0)

# Attach signal handler to capture termination (Ctrl+C)
signal.signal(signal.SIGINT, signal_handler)

# Define payment stages and status
STAGE_DETAILS = {
    1: "Payment initialized",
    2: "Payment in progress",
    3: "Fraud check completed",
    4: "Risk assessment completed",
    5: "Payment completed",
    6: "Payment finalization",
    7: "Audit completed"
}

PAYMENT_STATUS = ["Initiated", "Processing", "Fraud Checked", "Risk Assessed", "Completed", "Finalized", "Audited"]

# Helper function to generate and update messagePayload for a specific payment
def generate_payment_message(puid, current_stage=1, fraud_check=False, risk_level="Low", message_payload=None):
    """
    Generate or update the messagePayload dynamically based on the payment stage and additional checks.
    """
    timestamp = datetime.now(timezone.utc).isoformat()

    # If the messagePayload is not provided, initialize an empty structure
    if message_payload is None:
        message_payload = {}

    # Add the relevant stage data
    if current_stage == 1:
        message_payload["Header"] = {
            "puid": puid,
            "timestamp": timestamp,
            "stage": current_stage,
            "transactionId": str(uuid.uuid4())  # Unique Transaction ID
        }
    elif current_stage == 2:
        message_payload["Body"] = {
            "puid": puid,
            "stage": current_stage,
            "details": "Payment initiated",
            "amount": 100.0,
            "currency": "USD",
            "paymentType": "Domestic Transfer"
        }
    elif current_stage == 3:
        message_payload["Trailer"] = {
            "puid": puid,
            "stage": current_stage,
            "status": "Payment in progress",
            "additional_info": "Payment stage 1 completed"
        }
    elif current_stage == 4:
        message_payload["RiskAssessment"] = {
            "puid": puid,
            "stage": current_stage,
            "fraudCheck": fraud_check,
            "fraudStatus": "Flagged" if fraud_check else "Clean",
            "chargebackStatus": "None"
        }
    elif current_stage == 5:
        message_payload["FinalStage"] = {
            "puid": puid,
            "stage": current_stage,
            "status": "Final",
            "settlementAmount": 97.5,  # Example settlement amount
            "settlementStatus": "Completed"
        }
    elif current_stage == 6:
        message_payload["Audit"] = {
            "puid": puid,
            "stage": current_stage,
            "audit_timestamp": timestamp,
            "userId": "user123",  # Example user ID
            "operatorId": "op987",  # Example operator ID
            "actionDetails": "Transaction initiated, processed, and completed successfully",
            "internalComments": "No issues during processing"
        }

    return message_payload  # Return as a dictionary, not a JSON string

def insert_sample_data_to_spanner():
    """
    Continuously inserts sample records into the Spanner table.
    """
    instance = spanner_client.instance(SPANNER_INSTANCE_ID)
    database = instance.database(SPANNER_DATABASE_ID)

    while True:
        # Generate sample data
        puid = str(uuid.uuid4())[:16]  # Shorten UUID to 16 characters to fit the schema

        # Simulate fraud check and risk level for demonstration
        fraud_check = True  # Assume fraud check flagged the transaction
        risk_level = "High"  # Assume the payment is high-risk for demonstration

        # Initialize messagePayload as None to start fresh
        message_payload = None

        # Simulate each stage and update the messagePayload
        for current_stage in range(1, 7):
            message_payload = generate_payment_message(puid, current_stage, fraud_check, risk_level, message_payload)

        # Serialize message_payload to JSON string
        message_payload_json = json.dumps(message_payload)

        # Generate the `puidHash` by hashing the `puid` and then truncating to 32 characters
        puid_hash = hashlib.sha256(puid.encode('utf-8')).hexdigest()[:32]

        # Prepare data for insertion
        sample_data = {
            "puid": puid,  # Store the PUID as well
            "puidHash": puid_hash,  # 32 characters hash of PUID
            "messagePayload": message_payload_json,  # JSON string containing stages
            "createTimestamp": datetime.now(timezone.utc),
            "updatedTimestamp": datetime.now(timezone.utc),
            "processingNode": "Node1",  # Example processing node
            "currentState": "INITIAL",
            "paymentNotes": "First payment of the day",
            "PaymentStatus": "SUCCESS"
        }

        # Insert data into Spanner
        with database.batch() as batch:
            batch.insert(
                table=SPANNER_TABLE_NAME,
                columns=list(sample_data.keys()),
                values=[list(sample_data.values())],
            )

        # Log to BigQuery after insertion
        log_to_bigquery("Spanner", f"Inserted record: {sample_data}")
        logger.info(f"Inserted record into Spanner: {sample_data}")
        time.sleep(5)  # Wait before inserting the next record

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
    logger.info(f"Logged to BigQuery: {source} - {details}")

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
    last_check_time = datetime.now(timezone.utc) - timedelta(seconds=60)  # Start with a 1-minute delay

    logger.info("Monitoring BigQuery table for real-time updates...")
    while True:
        logger.info(f"Polling BigQuery at {datetime.now(timezone.utc).isoformat()}...")
        new_rows = fetch_new_data_from_bigquery(last_check_time)

        if new_rows:
            for row in new_rows:
                log_to_bigquery("BigQuery", f"New row: {dict(row)}")
                logger.info(f"New row from BigQuery: {dict(row)}")
            last_check_time = max(row.Timestamp for row in new_rows)  # Update the last check time to the most recent row's timestamp
        else:
            logger.info(f"No new rows found at {datetime.now(timezone.utc).isoformat()}.")
        time.sleep(POLL_INTERVAL)

def main():
    """
    Main function to start Spanner insertion and BigQuery monitoring concurrently.
    """
    try:
        logger.info("Starting payment processing simulation...")

        # Simulate payment processing
        spanner_thread = Thread(target=insert_sample_data_to_spanner)
        bigquery_thread = Thread(target=monitor_bigquery_table)  # Ensure this function is defined

        # Start threads
        spanner_thread.start()
        bigquery_thread.start()

        # Wait for both threads to complete
        spanner_thread.join()
        bigquery_thread.join()

    except KeyboardInterrupt:
        logger.info("Payment processing simulation interrupted by user.")
    except Exception as e:
        logger.error(f"An error occurred during payment processing: {str(e)}")

if __name__ == "__main__":
    main()
