#!/bin/bash

# Set the base URL for each service (adjust these URLs to match your actual Kubernetes service URLs)
TRANSACTION_SERVICE_URL="http://localhost:8080"  # Port forwarded transaction service
PAYMENT_SERVICE_URL="http://payment-service.app-ns.svc.cluster.local:8080"  # Assuming payment-service is accessible via DNS
RECONCILIATION_SERVICE_URL="http://reconciliation-service.app-ns.svc.cluster.local:8080"  # Assuming reconciliation-service is accessible via DNS

# Function to create a payment request
create_payment_request() {
  echo "Creating payment request..."

  # Sample payment request payload
  payload=$(cat <<EOF
{
  "transactionId": "11",
  "userId": "user111",
  "amount": 100.00
}
EOF
)

  echo "Sending payment request with transactionId: 11"
  
  # Send the payment request to the transaction service (via localhost)
  response=$(curl -s -X POST "$TRANSACTION_SERVICE_URL/transactions" \
    -H "Content-Type: application/json" \
    -d "$payload")

  # Debugging the response
  echo "Response from transaction service: $response"

  # Check if the transaction was successful
  if [[ $response == *"Transaction saved"* ]]; then
    echo "Payment request created successfully in transaction service."
  else
    echo "Failed to create payment request in transaction service."
    exit 1
  fi

  # Return the transactionId for further use
  echo "11"
}

# Function to check the status of the payment in the payment service
check_payment_status() {
  local transaction_id=$1  # Accept transaction_id as an argument
  echo "Checking payment status for transaction ID: $transaction_id in payment service..."

  # Check if the payment status is updated to "COMPLETED"
  payment_status=$(curl -s "$PAYMENT_SERVICE_URL/payments/$transaction_id/status")

  if [[ "$payment_status" == "COMPLETED" ]]; then
    echo "Payment processed and status updated to COMPLETED in payment service."
  else
    echo "Payment processing failed or status is not COMPLETED. Status: $payment_status"
    exit 1
  fi
}

# Function to check reconciliation status
check_reconciliation_status() {
  local transaction_id=$1  # Accept transaction_id as an argument
  echo "Checking reconciliation status for transaction ID: $transaction_id in reconciliation service..."

  # Fetch reconciliation status
  reconciliation_status=$(curl -s "$RECONCILIATION_SERVICE_URL/reconciliation/$transaction_id/status")

  if [[ "$reconciliation_status" == "COMPLETED" ]]; then
    echo "Reconciliation completed successfully."
  else
    echo "Reconciliation failed. Status: $reconciliation_status"
    exit 1
  fi
}

# Execute the end-to-end test
transaction_id=$(create_payment_request)
check_payment_status "$transaction_id"
check_reconciliation_status "$transaction_id"

echo "End-to-end payment flow test completed successfully."
