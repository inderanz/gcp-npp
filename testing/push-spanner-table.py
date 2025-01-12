import time
import uuid
from datetime import datetime
from google.cloud import spanner

# Configuration
INSTANCE_ID = "sample-instance"
DATABASE_ID = "audit-db"
TABLE_NAME = "payment_audit_trail"

def insert_sample_data():
    # Initialize Spanner client
    spanner_client = spanner.Client()
    instance = spanner_client.instance(INSTANCE_ID)
    database = instance.database(DATABASE_ID)

    # Generate sample data
    sample_data = {
        "PUID": str(uuid.uuid4()),  # Unique ID
        "Action": "CREATE_PAYMENT",
        "Status": "SUCCESS",
        "Timestamp": datetime.utcnow(),
        "ServiceName": "PaymentService",
        "Metadata": '{"amount": 100.50, "currency": "USD"}',  # JSON format
        "RetryCount": 0,
        "ErrorDetails": None,  # Nullable column
    }

    with database.batch() as batch:
        batch.insert(
            table=TABLE_NAME,
            columns=list(sample_data.keys()),
            values=[list(sample_data.values())],
        )
    print(f"Inserted record: {sample_data}")

def main():
    try:
        while True:
            insert_sample_data()
            time.sleep(11)  # Wait for 11 seconds between inserts
    except KeyboardInterrupt:
        print("\nProcess terminated by user.")

if __name__ == "__main__":
    main()
