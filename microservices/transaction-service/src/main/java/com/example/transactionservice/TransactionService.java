package com.example.transactionservice;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.DatabaseId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import com.fasterxml.jackson.databind.ObjectMapper;


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
        factory.setConnectTimeout(5000);  // Set connection timeout (5 seconds)
        factory.setReadTimeout(5000);  // Set read timeout (5 seconds)
        return factory;
    }

    public void processTransaction(TransactionRequest transactionRequest) {
        logger.info("Processing transaction: " + transactionRequest.toString());

        int retryCount = 0;

        try {
            saveTransaction(transactionRequest, "PENDING");

            boolean paymentSuccess = callPaymentService(transactionRequest, retryCount);
            boolean reconciliationSuccess = callReconciliationService(transactionRequest, retryCount);

            String finalStatus = (paymentSuccess && reconciliationSuccess) ? "COMPLETED" : "FAILED";
            updateTransactionStatus(transactionRequest.getPuid(), finalStatus);

        } catch (Exception e) {
            logger.severe("Transaction processing failed: " + e.getMessage());
            updateTransactionStatus(transactionRequest.getPuid(), "FAILED");
        }
    }

    private void saveTransaction(TransactionRequest transactionRequest, String status) {
        try {
            Mutation mutation = Mutation.newInsertOrUpdateBuilder("Transactions")
                    .set("PUID").to(transactionRequest.getPuid())
                    .set("UserId").to(transactionRequest.getUserId())
                    .set("Amount").to(transactionRequest.getAmount())
                    .set("Status").to(status)
                    .set("Timestamp").to(Timestamp.now())
                    .build();
            spannerClient.write(Collections.singletonList(mutation));
            logger.info("Transaction saved with status: " + status);
        } catch (Exception e) {
            logger.severe("Error saving transaction: " + e.getMessage());
            throw e;
        }
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
                logger.severe("Payment Service failed with status: " + response.getStatusCode());
                logAuditTrail(transactionRequest.getPuid(), "PaymentService", "PROCESS_PAYMENT", "FAILED", response.getBody(), retryCount, "Payment Service failed");
                return false;
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.severe("Payment Service failed with status code: " + e.getStatusCode() + " Response body: " + e.getResponseBodyAsString());
            logAuditTrail(transactionRequest.getPuid(), "PaymentService", "PROCESS_PAYMENT", "FAILED", e.getResponseBodyAsString(), retryCount, e.getMessage());
            return false;
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
                logger.severe("Reconciliation Service failed with status: " + response.getStatusCode());
                logAuditTrail(transactionRequest.getPuid(), "ReconciliationService", "PROCESS_RECONCILIATION", "FAILED", response.getBody(), retryCount, "Reconciliation Service failed");
                return false;
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.severe("Reconciliation Service failed with status code: " + e.getStatusCode() + " Response body: " + e.getResponseBodyAsString());
            logAuditTrail(transactionRequest.getPuid(), "ReconciliationService", "PROCESS_RECONCILIATION", "FAILED", e.getResponseBodyAsString(), retryCount, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.severe("Error calling Reconciliation Service: " + e.getMessage());
            logAuditTrail(transactionRequest.getPuid(), "ReconciliationService", "PROCESS_RECONCILIATION", "FAILED", "{}", retryCount, e.getMessage());
            return false;
        }
    }

    private void updateTransactionStatus(String puid, String status) {
        try {
            Mutation mutation = Mutation.newUpdateBuilder("Transactions")
                    .set("PUID").to(puid)
                    .set("Status").to(status)
                    .build();
            spannerClient.write(Collections.singletonList(mutation));
            logger.info("Transaction status updated to: " + status);

            logAuditTrail(puid, "PaymentService", "UPDATE_STATUS", status, "{}", 0, "");
        } catch (Exception e) {
            logger.severe("Error updating transaction status: " + e.getMessage());
        }
    }

    
    private void logAuditTrail(String puid, String serviceName, String action, String status, Object metadata, int retryCount, String errorDetails) {
        try {
            // Convert metadata object to JSON string
            ObjectMapper objectMapper = new ObjectMapper();
            String metadataJson = objectMapper.writeValueAsString(metadata);

            // Insert audit record into the PaymentAuditTrail table
            Mutation mutation = Mutation.newInsertOrUpdateBuilder("PaymentAuditTrail")
                    .set("PUID").to(puid)
                    .set("Action").to(action)
                    .set("Status").to(status)
                    .set("ServiceName").to(serviceName)
                    .set("Metadata").to(metadataJson)  // Store the JSON string
                    .set("RetryCount").to(retryCount)
                    .set("ErrorDetails").to(errorDetails)
                    .set("Timestamp").to(Timestamp.now())
                    .build();

            auditSpannerClient.write(Collections.singletonList(mutation));
            logger.info("Audit trail logged for PUID: " + puid + ", Action: " + action + ", Status: " + status);
        } catch (Exception e) {
            logger.severe("Error logging audit trail: " + e.getMessage());
        }
    }
}
