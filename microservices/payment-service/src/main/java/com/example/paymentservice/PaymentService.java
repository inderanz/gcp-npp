package com.example.paymentservice;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.DatabaseId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PaymentService {

    private static final Logger logger = Logger.getLogger(PaymentService.class.getName());

    private DatabaseClient spannerClient;
    private DatabaseClient auditSpannerClient;

    @Value("${spanner.project-id}")
    private String spannerProjectId;

    @Value("${spanner.instance-id}")
    private String spannerInstanceId;

    @Value("${spanner.database-name}")
    private String spannerDatabaseName;

    @Value("${audit.db.table}")
    private String auditTrailTable;

    // Ensure retry configuration
    private static final int MAX_RETRIES = 3;

    @PostConstruct
    public void initializeSpannerClient() {
        if (spannerProjectId == null || spannerInstanceId == null || spannerDatabaseName == null) {
            logger.severe("Spanner configuration is missing! Please check your environment variables or application properties.");
            throw new IllegalStateException("Missing Spanner configuration");
        }

        try {
            SpannerOptions spannerOptions = SpannerOptions.newBuilder()
                    .setProjectId(spannerProjectId)
                    .build();

            Spanner spanner = spannerOptions.getService();

            // Initialize DatabaseClient for the payments database
            DatabaseId dbId = DatabaseId.of(spannerProjectId, spannerInstanceId, spannerDatabaseName);
            this.spannerClient = spanner.getDatabaseClient(dbId);

            // Initialize DatabaseClient for the audit database
            DatabaseId auditDbId = DatabaseId.of(spannerProjectId, spannerInstanceId, "audit-db");
            this.auditSpannerClient = spanner.getDatabaseClient(auditDbId);

            logger.info("Spanner client initialized successfully.");
        } catch (Exception e) {
            logger.severe("Failed to initialize Spanner client: " + e.getMessage());
            throw new IllegalStateException("Spanner client initialization failed", e);
        }
    }

    public void processPayment(PaymentRequest paymentRequest) {
        String status = "PENDING"; // Default status when processing the payment
        int retryCount = 0;  // Initialize retry count

        try {
            // Step 1: Save the payment with PENDING status in the Payments table
            savePayment(paymentRequest, status);

            // Step 2: Call Payment Service and handle retries
            boolean paymentSuccess = callPaymentService(paymentRequest, retryCount);

            // Step 3: Update the status after processing
            status = paymentSuccess ? "COMPLETED" : "FAILED";
            updatePaymentStatus(paymentRequest.getPuid(), status);

        } catch (Exception e) {
            logger.severe("Payment processing failed for PUID " + paymentRequest.getPuid() + ": " + e.getMessage());
            updatePaymentStatus(paymentRequest.getPuid(), "FAILED");
        }
    }

    private void savePayment(PaymentRequest paymentRequest, String status) {
        try {
            Mutation mutation = Mutation.newInsertOrUpdateBuilder("Payments")
                    .set("PaymentUID").to(paymentRequest.getPuid())
                    .set("UserId").to(paymentRequest.getUserId())
                    .set("Amount").to(paymentRequest.getAmount())
                    .set("Status").to(status)
                    .set("Timestamp").to(Timestamp.now())
                    .build();
            spannerClient.write(Collections.singletonList(mutation));
            logger.info("Payment saved with PUID: " + paymentRequest.getPuid());
        } catch (Exception e) {
            logger.severe("Error saving payment: " + e.getMessage());
            throw e;
        }
    }

    private boolean callPaymentService(PaymentRequest paymentRequest, int retryCount) {
        boolean success = false;
        while (retryCount < MAX_RETRIES && !success) {
            try {
                logger.info("Calling Payment Service...");
                // Simulate payment service call (use real API call in a production scenario)
                // ResponseEntity<String> response = restTemplate.postForEntity(paymentServiceUrl + "/payments", paymentRequest, String.class);
                
                // For now, assume the response is successful
                String response = "Payment processed successfully!";
                logger.info("Payment Service Response: " + response);

                // Log the audit trail with success
                logAuditTrail(paymentRequest.getPuid(), "PaymentService", "PROCESS_PAYMENT", "COMPLETED", response, retryCount, "");
                success = true;
            } catch (Exception e) {
                retryCount++;
                logger.severe("Payment Service failed. Retry count: " + retryCount + ". Error: " + e.getMessage());

                // Log failure with retries and error message
                logAuditTrail(paymentRequest.getPuid(), "PaymentService", "PROCESS_PAYMENT", "FAILED", "{}", retryCount, e.getMessage());
            }
        }
        return success;
    }

    private void updatePaymentStatus(String puid, String status) {
        try {
            Mutation mutation = Mutation.newUpdateBuilder("Payments")
                    .set("PaymentUID").to(puid)
                    .set("Status").to(status)
                    .build();
            spannerClient.write(Collections.singletonList(mutation));
            logger.info("Payment status updated to: " + status);

            // Log the status update in the audit table
            logAuditTrail(puid, "PaymentService", "UPDATE_STATUS", status, "{}", 0, "");
        } catch (Exception e) {
            logger.severe("Error updating payment status: " + e.getMessage());
        }
    }

    private void logAuditTrail(String puid, String serviceName, String action, String status, String metadata, int retryCount, String errorDetails) {
        try {
            // Convert metadata object to JSON string
            ObjectMapper objectMapper = new ObjectMapper();
            String metadataJson = objectMapper.writeValueAsString(metadata);

            Mutation mutation = Mutation.newInsertOrUpdateBuilder(auditTrailTable)
                    .set("PUID").to(puid)
                    .set("ServiceName").to(serviceName)
                    .set("Action").to(action)
                    .set("Status").to(status)
                    .set("Metadata").to(metadataJson)  // Store any relevant metadata
                    .set("Timestamp").to(Timestamp.now())
                    .set("RetryCount").to(retryCount)
                    .set("ErrorDetails").to(errorDetails)  // Detailed error message if any
                    .build();
            auditSpannerClient.write(Collections.singletonList(mutation));
            logger.info("Audit trail logged for PUID: " + puid + ", Action: " + action + ", Status: " + status);
        } catch (Exception e) {
            logger.severe("Error logging audit trail: " + e.getMessage());
        }
    }
}
