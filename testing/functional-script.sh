#!/bin/bash

# Set the base URL for each service (adjust these URLs to match your actual Kubernetes service URLs)
TRANSACTION_SERVICE_URL="http://localhost:8080"  # Port forwarded transaction service
PAYMENT_SERVICE_URL="http://payment-service.app-ns.svc.cluster.local:8080"  # Payment service accessible via DNS
RECONCILIATION_SERVICE_URL="http://reconciliation-service.app-ns.svc.cluster.local:8080"  # Reconciliation service accessible via DNS

# Function to generate a random PUID
generate_random_puid() {
  echo "$(date +%s%N | md5 | head -c 8)"
}

# Function to create a payment request
create_payment_request() {
  local puid=$1
  local user_id="User-$puid"
  local amount=$((RANDOM % 1000 + 1)).00  # Generate random amount between 1.00 and 1000.00

  echo "Creating payment request for PUID: $puid, UserId: $user_id, Amount: $amount"

  # Generate the payload
  payload=$(cat <<EOF
{
  "puid": "$puid",
  "userId": "$user_id",
  "amount": $amount
}
EOF
)

  # Send the payment request to the transaction service
  response=$(curl -s -w "%{http_code}" -X POST "$TRANSACTION_SERVICE_URL/transactions" \
    -H "Content-Type: application/json" \
    -d "$payload")

  # Extract response code and body
  response_code=$(echo "$response" | tail -c 4)
  response_body=$(echo "$response" | head -c -4)

  # Check if the transaction was successfully created
  if [[ "$response_code" == "200" ]]; then
    echo "Payment request created successfully for PUID: $puid"
  else
    echo "Failed to create payment request for PUID: $puid. Response Code: $response_code"
    echo "Response Body: $response_body"
  fi
}

# Function to generate requests at a sustained rate
generate_requests() {
  local rate=1  # Requests per second
  local interval=$(awk "BEGIN {print 1/$rate}")  # Interval between requests in seconds

  echo "Starting to generate requests at a rate of $rate requests per second. Press Ctrl+C to stop."

  while true; do
    puid=$(generate_random_puid)
    create_payment_request "$puid" &
    sleep "$interval"
  done
}

# Start generating requests
generate_requests
