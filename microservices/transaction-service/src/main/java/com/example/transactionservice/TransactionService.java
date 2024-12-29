package com.example.transactionservice;

import com.google.cloud.spanner.*;
import com.google.cloud.Timestamp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;


import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.logging.Logger;

@Service
public class TransactionService {

    private static final Logger logger = Logger.getLogger(TransactionService.class.getName());

    private DatabaseClient spannerClient;
    private DatabaseClient auditSpannerClient;

    private final RestTemplate restTemplate;

    @Value("${spanner.project-id}")
    private String spannerProjectId;

    @Value("${spanner.instance-id}")
    private String spannerInstanceId;

    @Value("${spanner.shared-db-name}")
    private String sharedDbName;

    @Value("${spanner.audit-db-name}")
    private String auditDbName;

    @Value("${payment.service.url}")
    private String paymentServiceUrl;

    @Value("${reconciliation.service.url}")
    private String reconciliationServiceUrl;

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

    public TransactionService() {
        this.restTemplate = new RestTemplate(createRequestFactory());
    }

    @PostConstruct
    public void initializeSpannerClient() {
        logger.info("Initializing Spanner client...");
        try {
            SpannerOptions spannerOptions = SpannerOptions.newBuilder()
                    .setProjectId(spannerProjectId)
                    .build();

            Spanner spanner = spannerOptions.getService();

            DatabaseId sharedDbId = DatabaseId.of(spannerProjectId, spannerInstanceId, sharedDbName);
            this.spannerClient = spanner.getDatabaseClient(sharedDbId);

            DatabaseId auditDbId = DatabaseId.of(spannerProjectId, spannerInstanceId, auditDbName);
            this.auditSpannerClient = spanner.getDatabaseClient(auditDbId);

            logger.info("Spanner client initialized successfully.");
        } catch (Exception e) {
            logger.severe("Failed to initialize Spanner client: " + e.getMessage());
            throw e;
        }
    }

    private ClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 seconds
        factory.setReadTimeout(10000); // 10 seconds
        return factory;
    }

    public void processTransaction(TransactionRequest transactionRequest) {
        logger.info("Processing transaction: " + transactionRequest);

        try {
            // Step 1: Save transaction with PENDING status
            saveTransaction(transactionRequest, "PENDING");

            // Step 2: Log the audit trail for saving transaction
            logAuditTrail(transactionRequest.getPuid(), "TransactionService", "SAVE_TRANSACTION", "PENDING", transactionRequest, 0, "");

            // Step 3: Call Payment Service with retry mechanism
            boolean paymentSuccess = retryWithDelay(() -> callPaymentService(transactionRequest, 0), MAX_RETRIES);

            // Step 4: Call Reconciliation Service with retry mechanism
            boolean reconciliationSuccess = retryWithDelay(() -> callReconciliationService(transactionRequest, 0), MAX_RETRIES);

            // Step 5: Update transaction status based on success or failure
            String finalStatus = (paymentSuccess && reconciliationSuccess) ? "COMPLETED" : "FAILED";
            updateTransactionStatus(transactionRequest.getPuid(), finalStatus);

            // Step 6: Log the audit trail for status update
            logAuditTrail(transactionRequest.getPuid(), "TransactionService", "UPDATE_STATUS", finalStatus, "{}", 0, "");

        } catch (Exception e) {
            logger.severe("Transaction processing failed: " + e.getMessage());
            updateTransactionStatus(transactionRequest.getPuid(), "FAILED");
            logAuditTrail(transactionRequest.getPuid(), "TransactionService", "PROCESS_TRANSACTION", "FAILED", "{}", 0, e.getMessage());
        }
    }

    private void saveTransaction(TransactionRequest transactionRequest, String status) {
        Mutation mutation = Mutation.newInsertOrUpdateBuilder("Transactions")
                .set("PUID").to(transactionRequest.getPuid())
                .set("UserId").to(transactionRequest.getUserId())
                .set("Amount").to(transactionRequest.getAmount())
                .set("Status").to(status)
                .set("Timestamp").to(Timestamp.now())
                .build();
        spannerClient.write(Collections.singletonList(mutation));
    }

    private boolean callPaymentService(TransactionRequest transactionRequest, int retryCount) {
        try {
            logger.info("Calling Payment Service...");
            ResponseEntity<String> response = restTemplate.postForEntity(
                    paymentServiceUrl + "/payments",
                    transactionRequest,
                    String.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Payment Service Response: " + response.getBody());
                logAuditTrail(transactionRequest.getPuid(), "PaymentService", "PROCESS_PAYMENT", "COMPLETED", response.getBody(), retryCount, "");
                return true;
            } else {
                logger.warning("Payment Service failed with status: " + response.getStatusCode());
                logAuditTrail(transactionRequest.getPuid(), "PaymentService", "PROCESS_PAYMENT", "FAILED", response.getBody(), retryCount, "Payment Service failed");
                return false;
            }
        } catch (Exception e) {
            logger.severe("Error calling Payment Service: " + e.getMessage());
            logAuditTrail(transactionRequest.getPuid(), "PaymentService", "PROCESS_PAYMENT", "FAILED", "{}", retryCount, e.getMessage());
            return false;
        }
    }

    private boolean callReconciliationService(TransactionRequest transactionRequest, int retryCount) {
        try {
            logger.info("Calling Reconciliation Service...");
            ResponseEntity<String> response = restTemplate.postForEntity(
                    reconciliationServiceUrl + "/reconciliation",
                    transactionRequest,
                    String.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Reconciliation Service Response: " + response.getBody());
                logAuditTrail(transactionRequest.getPuid(), "ReconciliationService", "PROCESS_RECONCILIATION", "COMPLETED", response.getBody(), retryCount, "");
                return true;
            } else {
                logger.warning("Reconciliation Service failed with status: " + response.getStatusCode());
                logAuditTrail(transactionRequest.getPuid(), "ReconciliationService", "PROCESS_RECONCILIATION", "FAILED", response.getBody(), retryCount, "Reconciliation Service failed");
                return false;
            }
        } catch (Exception e) {
            logger.severe("Error calling Reconciliation Service: " + e.getMessage());
            logAuditTrail(transactionRequest.getPuid(), "ReconciliationService", "PROCESS_RECONCILIATION", "FAILED", "{}", retryCount, e.getMessage());
            return false;
        }
    }

    private void updateTransactionStatus(String puid, String status) {
        Mutation mutation = Mutation.newUpdateBuilder("Transactions")
                .set("PUID").to(puid)
                .set("Status").to(status)
                .build();
        spannerClient.write(Collections.singletonList(mutation));
    }

    private void logAuditTrail(String puid, String serviceName, String action, String status, Object metadata, int retryCount, String errorDetails) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String metadataJson = objectMapper.writeValueAsString(metadata);

            Mutation mutation = Mutation.newInsertOrUpdateBuilder("payment_audit_trail")
                    .set("PUID").to(puid)
                    .set("Action").to(action)
                    .set("Status").to(status)
                    .set("ServiceName").to(serviceName)
                    .set("Metadata").to(metadataJson)
                    .set("RetryCount").to(retryCount)
                    .set("ErrorDetails").to(errorDetails)
                    .set("Timestamp").to(Timestamp.now())
                    .build();

            auditSpannerClient.write(Collections.singletonList(mutation));
            logger.info("Audit trail logged successfully for PUID: " + puid);

            boolean isValidated = validateAuditTrailEntry(puid, action, status);
            if (!isValidated) {
                logger.warning("Audit trail entry validation failed for PUID: " + puid + ", Action: " + action);
            }
        } catch (Exception e) {
            logger.severe("Error logging audit trail: " + e.getMessage());
        }
    }

    private boolean validateAuditTrailEntry(String puid, String action, String status) {
        try {
            String query = "SELECT COUNT(*) AS entry_count FROM payment_audit_trail WHERE PUID = @puid AND Action = @action AND Status = @status";

            Statement statement = Statement.newBuilder(query)
                    .bind("puid").to(puid)
                    .bind("action").to(action)
                    .bind("status").to(status)
                    .build();

            try (ResultSet resultSet = auditSpannerClient.singleUse().executeQuery(statement)) {
                if (resultSet.next()) {
                    long count = resultSet.getLong("entry_count");
                    return count > 0;
                }
            }
        } catch (Exception e) {
            logger.severe("Error validating audit trail entry: " + e.getMessage());
        }
        return false;
    }

    private boolean retryWithDelay(RunnableWithBoolean operation, int maxRetries) {
        int retryCount = 0;
        while (retryCount < maxRetries) {
            if (operation.run()) {
                return true;
            }
            retryCount++;
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface RunnableWithBoolean {
        boolean run();
    }
}
