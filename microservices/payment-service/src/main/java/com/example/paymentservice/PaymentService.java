package com.example.paymentservice;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.Timestamp;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.logging.Logger;

@Service
public class PaymentService {

    private static final Logger logger = Logger.getLogger(PaymentService.class.getName());

    private final DatabaseClient spannerClient;

    // Hardcoded values for Spanner instance, project, and database
    private static final String SPANNER_PROJECT_ID = "spanner-gke-443910"; // Your Google Cloud Project ID
    private static final String SPANNER_INSTANCE_ID = "sample-instance"; // Your Spanner Instance ID
    private static final String SPANNER_DATABASE_NAME = "sample-game"; // Your Spanner Database Name

    public PaymentService() {
        // Build SpannerOptions with the hardcoded project ID
        SpannerOptions.Builder optionsBuilder = SpannerOptions.newBuilder()
                .setProjectId(SPANNER_PROJECT_ID);

        // Initialize Spanner client
        Spanner spanner = optionsBuilder.build().getService();

        // Create the DatabaseClient using hardcoded values
        DatabaseId dbId = DatabaseId.of(SPANNER_PROJECT_ID, SPANNER_INSTANCE_ID, SPANNER_DATABASE_NAME);
        this.spannerClient = spanner.getDatabaseClient(dbId);
    }

    /**
     * Process a payment by writing the data to the Spanner database
     * @param paymentRequest the details of the payment to be processed
     */
    public void processPayment(PaymentRequest paymentRequest) {
        // Default status when inserting the payment record
        String status = "PENDING"; // Default status when processing the payment
        Instant timestamp = Instant.now();  // Get the current timestamp

        try {
            // Convert Instant to Spanner Timestamp
            Timestamp spannerTimestamp = Timestamp.ofTimeSecondsAndNanos(timestamp.getEpochSecond(), timestamp.getNano());  // Corrected line

            // Create a Mutation (Insert) to write the payment data to the "Payments" table
            Mutation mutation = Mutation.newInsertBuilder("Payments")
                    .set("PaymentUID").to(paymentRequest.getPuid())
                    .set("UserId").to(paymentRequest.getUserId())
                    .set("Amount").to(paymentRequest.getAmount())
                    .set("Status").to(status)
                    .set("Timestamp").to(spannerTimestamp)  // Use the Spanner Timestamp
                    .build();

            // Insert the new row if it doesn't exist
            spannerClient.write(Collections.singletonList(mutation));

            // After payment is processed, update the status to "COMPLETED" (or other logic)
            status = "COMPLETED";

        } catch (Exception e) {
            // If an error occurs, update the status to "FAILED"
            status = "FAILED";
            logger.severe("Payment failed for PUID " + paymentRequest.getPuid() + ": " + e.getMessage());
        }

        // Update the payment status in the database after the mutation
        Mutation statusMutation = Mutation.newUpdateBuilder("Payments")
                .set("Status").to(status)
                .set("PaymentUID").to(paymentRequest.getPuid())
                .build();

        // This mutation ensures the status is updated after the payment process
        spannerClient.write(Collections.singletonList(statusMutation));
    }
}
