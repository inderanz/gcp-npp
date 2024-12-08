package com.example.transactionservice;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.DatabaseId;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class TransactionService {

    private final DatabaseClient spannerClient;

    // Hardcoded values for Spanner instance, project, and database
    private static final String SPANNER_PROJECT_ID = "spanner-gke-443910"; // Your Google Cloud Project ID
    private static final String SPANNER_INSTANCE_ID = "sample-instance"; // Your Spanner Instance ID
    private static final String SPANNER_DATABASE_NAME = "sample-game"; // Your Spanner Database Name

    public TransactionService() {
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
     * Process a transaction by writing the data to the Spanner database
     * @param transactionRequest the details of the transaction to be processed
     */
    public void processTransaction(TransactionRequest transactionRequest) {
        // Ensure that the Status field is provided for Payments table.
        // If 'Status' is not part of your request body, set a default value.
        Mutation mutation = Mutation.newInsertOrUpdateBuilder("Payments")
                .set("PaymentUID").to(transactionRequest.getTransactionId())  // Set the payment ID
                .set("UserId").to(transactionRequest.getUserId())              // Set the user ID
                .set("Amount").to(transactionRequest.getAmount())              // Set the transaction amount
                .set("Status").to("PENDING")                                   // Set a default status (PENDING)
                .set("Timestamp").to(com.google.cloud.Timestamp.now())         // Set current timestamp
                .build();

        // Perform the write operation to Spanner (on Payments table)
        spannerClient.write(Collections.singletonList(mutation));
    }
}
