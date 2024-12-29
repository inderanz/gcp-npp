#!/bin/bash

# Set the base URL for each service (adjust these URLs to match your actual Kubernetes service URLs)
TRANSACTION_SERVICE_URL="http://localhost:8080"  # Port forwarded transaction service
PAYMENT_SERVICE_URL="http://payment-service.app-ns.svc.cluster.local:8080"  # Payment service accessible via DNS
RECONCILIATION_SERVICE_URL="http://reconciliation-service.app-ns.svc.cluster.local:8080"  # Reconciliation service accessible via DNS

# Function to create a payment request
create_payment_request() {
  echo "Creating payment request..."

  # Sample payment request payload with PUID as unique identifier
  payload=$(cat <<EOF
{
  "puid": "02675",
  "userId": "Inder-02675",
  "amount": 101.00
}
EOF
)

  # Send the payment request to the transaction service (via localhost)
  response=$(curl -s -X POST "$TRANSACTION_SERVICE_URL/transactions" \
    -H "Content-Type: application/json" \
    -d "$payload")

  # Extract the response code and body for debugging
  response_code=$(echo "$response" | head -n 1 | awk '{print $2}')
  response_body=$(echo "$response" | tail -n +2)

  # Check if the transaction was successfully created
  echo "Response code: $response_code"
  echo "Response body: $response_body"

  if [[ "$response_code" == "200" ]]; then
    echo "Payment request created successfully in transaction service."
  else
    echo "Failed to create payment request in transaction service."
    echo "Response Body: $response_body"
    exit 1
  fi
}

# Function to check the status of the payment in the payment service
check_payment_status() {
  echo "Checking payment status in payment service..."

  # Fetch payment status using PUID (the same unique identifier)
  payment_status=$(curl -s "$PAYMENT_SERVICE_URL/payments/12345/status")

  if [[ "$payment_status" == "COMPLETED" ]]; then
    echo "Payment processed and status updated to COMPLETED in payment service."
  else
    echo "Payment processing failed or status is not COMPLETED."
    exit 1
  fi
}

# Function to check reconciliation status
check_reconciliation_status() {
  echo "Checking reconciliation status in reconciliation service..."

  # Fetch reconciliation status using PUID
  reconciliation_status=$(curl -s "$RECONCILIATION_SERVICE_URL/reconciliation/12345/status")

  if [[ "$reconciliation_status" == "COMPLETED" ]]; then
    echo "Reconciliation completed successfully."
  else
    echo "Reconciliation failed."
    exit 1
  fi
}

# Execute the end-to-end test
create_payment_request
check_payment_status
check_reconciliation_status

echo "End-to-end payment flow test completed successfully."
