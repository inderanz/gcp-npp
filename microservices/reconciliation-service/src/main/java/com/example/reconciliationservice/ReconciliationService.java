package com.example.reconciliationservice;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Mutation;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class ReconciliationService {

    private final DatabaseClient spannerClient;

    // Hardcoded values for Spanner instance and database
    private static final String SPANNER_INSTANCE_ID = "sample-instance";
    private static final String SPANNER_DATABASE = "sample-game";

    public ReconciliationService() {
        // Build SpannerOptions
        SpannerOptions.Builder optionsBuilder = SpannerOptions.newBuilder()
                .setProjectId("spanner-gke-443910"); // Your Google Cloud project ID

        // Initialize Spanner client
        SpannerOptions options = optionsBuilder.build();
        com.google.cloud.spanner.Spanner spanner = options.getService();

        // Create the DatabaseClient
        DatabaseId dbId = DatabaseId.of("spanner-gke-443910", SPANNER_INSTANCE_ID, SPANNER_DATABASE);
        this.spannerClient = spanner.getDatabaseClient(dbId);
    }

    public void processReconciliation(ReconciliationRequest reconciliationRequest) {
        Mutation mutation = Mutation.newInsertOrUpdateBuilder("PaymentState")
                .set("PUID").to(reconciliationRequest.getPuid())
                .set("Status").to(reconciliationRequest.getStatus())
                .build();

        spannerClient.write(Collections.singletonList(mutation));
    }

    // Add the method to fetch payment state based on PUID
    public String fetchPaymentState(String puid) {
        // Create a query to fetch payment state
        String query = "SELECT Status FROM PaymentState WHERE PUID = @puid";
        
        // Set parameters
        com.google.cloud.spanner.Statement statement = com.google.cloud.spanner.Statement.newBuilder(query)
                .bind("puid").to(puid)
                .build();

        // Execute query
        ResultSet resultSet = spannerClient.singleUse().executeQuery(statement);
        
        if (resultSet.next()) {
            return resultSet.getString("Status");
        } else {
            return "Payment State Not Found";
        }
    }
}
