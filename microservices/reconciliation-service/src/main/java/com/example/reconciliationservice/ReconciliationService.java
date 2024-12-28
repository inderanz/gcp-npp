package com.example.reconciliationservice;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.logging.Logger;

@Service
public class ReconciliationService {

    private static final Logger logger = Logger.getLogger(ReconciliationService.class.getName());
    private final RestTemplate restTemplate = new RestTemplate();
    private static final int MAX_RETRIES = 3; // Maximum retry attempts

    private DatabaseClient spannerClient;
    private DatabaseClient auditSpannerClient;

    @Value("${spanner.project-id}")
    private String spannerProjectId;

    @Value("${spanner.instance-id}")
    private String spannerInstanceId;

    @Value("${spanner.database-name}")
    private String spannerDatabaseName;

    @Value("${audit.db.name}")
    private String auditDbName;

    @Value("${reconciliation.audit.table}")
    private String reconciliationAuditTable;

    @Value("${payment.service.url}")
    private String paymentServiceUrl;

    @Value("${reconciliation.service.url}")
    private String reconciliationServiceUrl;

    @PostConstruct
    public void initializeSpannerClient() {
        logger.info("Initializing Spanner client...");

        try {
            SpannerOptions spannerOptions = SpannerOptions.newBuilder()
                    .setProjectId(spannerProjectId)
                    .build();
            Spanner spanner = spannerOptions.getService();

            // Initialize Spanner client for reconciliation database
            DatabaseId dbId = DatabaseId.of(spannerProjectId, spannerInstanceId, spannerDatabaseName);
            this.spannerClient = spanner.getDatabaseClient(dbId);

            // Initialize Spanner client for audit database
            DatabaseId auditDbId = DatabaseId.of(spannerProjectId, spannerInstanceId, auditDbName);
            this.auditSpannerClient = spanner.getDatabaseClient(auditDbId);

            logger.info("Spanner client initialized successfully.");
        } catch (Exception e) {
            logger.severe("Failed to initialize Spanner client: " + e.getMessage());
            throw new IllegalStateException("Spanner configuration is missing or invalid.");
        }
    }

    public void processReconciliation(ReconciliationRequest reconciliationRequest) {
        String status = "PENDING"; // Default status when reconciling the payment

        int retryCount = 0;

        try {
            // Step 1: Save the reconciliation with PENDING status in the reconciliation database
            saveReconciliation(reconciliationRequest, status);

            // Step 2: Call Payment Service with retry logic
            boolean paymentSuccess = false;
            while (retryCount < MAX_RETRIES) {
                paymentSuccess = callPaymentService(reconciliationRequest);
                if (paymentSuccess) break;
                retryCount++;
            }

            // Step 3: Ensure that the ReconciliationService does not call itself
            if (!isReconciliationService(reconciliationRequest)) {
                // Step 4: Call Reconciliation Service with retry logic
                boolean reconciliationSuccess = false;
                retryCount = 0;
                while (retryCount < MAX_RETRIES) {
                    reconciliationSuccess = callReconciliationService(reconciliationRequest);
                    if (reconciliationSuccess) break;
                    retryCount++;
                }
                status = (paymentSuccess && reconciliationSuccess) ? "COMPLETED" : "FAILED";
            } else {
                // If it's a recursive call, mark as failed
                logger.severe("Reconciliation service called itself, avoiding recursive call.");
                status = "FAILED";
            }

            // Step 5: Update reconciliation status
            updateReconciliationStatus(reconciliationRequest.getPuid(), status);

        } catch (Exception e) {
            logger.severe("Reconciliation processing failed: " + e.getMessage());
            updateReconciliationStatus(reconciliationRequest.getPuid(), "FAILED");
        }
    }

    private boolean isReconciliationService(ReconciliationRequest reconciliationRequest) {
        // Prevent recursive calls to Reconciliation Service
        // Check if this request is originating from another ReconciliationService
        return reconciliationRequest.getSourceService() != null &&
                reconciliationRequest.getSourceService().equals("ReconciliationService");
    }

    private void saveReconciliation(ReconciliationRequest reconciliationRequest, String status) {
        try {
            Mutation mutation = Mutation.newInsertOrUpdateBuilder("Reconciliation")
                    .set("PUID").to(reconciliationRequest.getPuid())
                    .set("Amount").to(reconciliationRequest.getAmount())
                    .set("Status").to(status)
                    .set("Timestamp").to(Timestamp.now())
                    .build();
            spannerClient.write(Collections.singletonList(mutation));
            logger.info("Reconciliation saved with status: " + status);
        } catch (Exception e) {
            logger.severe("Error saving reconciliation: " + e.getMessage());
            throw e;
        }
    }

    private boolean callPaymentService(ReconciliationRequest reconciliationRequest) {
        try {
            logger.info("Calling Payment Service...");
            ResponseEntity<String> response = restTemplate.postForEntity(
                    paymentServiceUrl + "/payments",
                    reconciliationRequest,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Payment Service Response: " + response.getBody());
                return true;
            } else {
                logger.severe("Payment Service failed with status: " + response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.severe("Error calling Payment Service: " + e.getMessage());
            return false;
        }
    }

    private boolean callReconciliationService(ReconciliationRequest reconciliationRequest) {
        try {
            logger.info("Calling Reconciliation Service...");
            ResponseEntity<String> response = restTemplate.postForEntity(
                    reconciliationServiceUrl + "/reconciliation",
                    reconciliationRequest,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Reconciliation Service Response: " + response.getBody());
                return true;
            } else {
                logger.severe("Reconciliation Service failed with status: " + response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.severe("Error calling Reconciliation Service: " + e.getMessage());
            return false;
        }
    }

    private void updateReconciliationStatus(String puid, String status) {
        try {
            Mutation mutation = Mutation.newUpdateBuilder("Reconciliation")
                    .set("PUID").to(puid)
                    .set("Status").to(status)
                    .build();
            spannerClient.write(Collections.singletonList(mutation));
            logger.info("Reconciliation status updated to: " + status);

            // Log audit trail in the audit database
            logAuditTrail(puid, "UPDATE_STATUS", status);
        } catch (Exception e) {
            logger.severe("Error updating reconciliation status: " + e.getMessage());
        }
    }

    private void logAuditTrail(String puid, String action, String status) {
        try {
            Mutation mutation = Mutation.newInsertBuilder(reconciliationAuditTable)
                    .set("PUID").to(puid)
                    .set("Action").to(action)
                    .set("Status").to(status)
                    .set("Timestamp").to(Timestamp.now())
                    .build();
            auditSpannerClient.write(Collections.singletonList(mutation));
            logger.info("Audit trail logged for PUID: " + puid + ", Action: " + action + ", Status: " + status);
        } catch (Exception e) {
            logger.severe("Error logging audit trail: " + e.getMessage());
        }
    }
}
