package com.example.reconciliationservice;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.logging.Logger;

@Service
public class ReconciliationService {

    private static final Logger logger = Logger.getLogger(ReconciliationService.class.getName());
    private RestTemplate restTemplate;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

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

    @Value("${payment.service.url}")
    private String paymentServiceUrl;

    @Value("${reconciliation.service.url}")
    private String reconciliationServiceUrl;

    @PostConstruct
    public void initialize() {
        initializeSpannerClient();
        configureRestTemplate();
    }

    private void initializeSpannerClient() {
        logger.info("Initializing Spanner client...");
        try {
            SpannerOptions spannerOptions = SpannerOptions.newBuilder()
                    .setProjectId(spannerProjectId)
                    .build();
            Spanner spanner = spannerOptions.getService();

            DatabaseId dbId = DatabaseId.of(spannerProjectId, spannerInstanceId, spannerDatabaseName);
            this.spannerClient = spanner.getDatabaseClient(dbId);

            DatabaseId auditDbId = DatabaseId.of(spannerProjectId, spannerInstanceId, auditDbName);
            this.auditSpannerClient = spanner.getDatabaseClient(auditDbId);

            logger.info("Spanner client initialized successfully.");
        } catch (Exception e) {
            logger.severe("Failed to initialize Spanner client: " + e.getMessage());
            throw new IllegalStateException("Spanner configuration is missing or invalid.");
        }
    }

    private void configureRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000); // 15 seconds
        factory.setReadTimeout(15000);    // 15 seconds
        this.restTemplate = new RestTemplate(factory);
        logger.info("RestTemplate configured with timeouts.");
    }

    public void processReconciliation(ReconciliationRequest reconciliationRequest) {
        logger.info("Processing reconciliation for PUID: " + reconciliationRequest.getPuid());
        String status = "PENDING";

        try {
            logger.info("Step 1: Saving reconciliation with status: PENDING...");
            saveReconciliation(reconciliationRequest, status);

            logger.info("Step 2: Calling Payment Service...");
            boolean paymentSuccess = retryWithDelay(() -> callPaymentService(reconciliationRequest), MAX_RETRIES);

            logger.info("Step 3: Calling Reconciliation Service...");
            boolean reconciliationSuccess = retryWithDelay(() -> callReconciliationService(reconciliationRequest), MAX_RETRIES);

            status = (paymentSuccess && reconciliationSuccess) ? "COMPLETED" : "FAILED";
            logger.info("Step 4: Updating reconciliation status to: " + status);
            updateReconciliationStatus(reconciliationRequest.getPuid(), status);

        } catch (Exception e) {
            logger.severe("Reconciliation processing failed: " + e.getMessage());
            updateReconciliationStatus(reconciliationRequest.getPuid(), "FAILED");
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
                logger.warning("Payment Service failed with status: " + response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.severe("Error calling Payment Service: " + e.getMessage());
            return false;
        }
    }

    private boolean callReconciliationService(ReconciliationRequest reconciliationRequest) {
        if (isReconciliationService(reconciliationRequest)) {
            logger.warning("Recursive call detected. Aborting reconciliation request for PUID: " + reconciliationRequest.getPuid());
            return false;
        }

        try {
            logger.info("Calling Reconciliation Service at: " + reconciliationServiceUrl);
            reconciliationRequest.setSourceService("ReconciliationService");
            ResponseEntity<String> response = restTemplate.postForEntity(
                    reconciliationServiceUrl + "/reconciliation",
                    reconciliationRequest,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Reconciliation Service Response: " + response.getBody());
                return true;
            } else {
                logger.warning("Reconciliation Service failed with status: " + response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.severe("Error calling Reconciliation Service: " + e.getMessage());
            return false;
        }
    }

    private boolean isReconciliationService(ReconciliationRequest reconciliationRequest) {
        return "ReconciliationService".equalsIgnoreCase(reconciliationRequest.getSourceService());
    }

    private void saveReconciliation(ReconciliationRequest reconciliationRequest, String status) {
        executeSpannerWrite(() -> {
            Mutation mutation = Mutation.newInsertOrUpdateBuilder("Reconciliation")
                    .set("PUID").to(reconciliationRequest.getPuid())
                    .set("Amount").to(reconciliationRequest.getAmount())
                    .set("Status").to(status)
                    .set("Timestamp").to(Timestamp.now())
                    .build();
            spannerClient.write(Collections.singletonList(mutation));
        });
    }

    private void updateReconciliationStatus(String puid, String status) {
        executeSpannerWrite(() -> {
            Mutation mutation = Mutation.newUpdateBuilder("Reconciliation")
                    .set("PUID").to(puid)
                    .set("Status").to(status)
                    .build();
            spannerClient.write(Collections.singletonList(mutation));
        });
    }

    private void executeSpannerWrite(Runnable writeOperation) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                writeOperation.run();
                return;
            } catch (Exception e) {
                logger.warning("Transient error writing to Spanner, attempt " + (attempt + 1) + ": " + e.getMessage());
                attempt++;
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Spanner write interrupted", ie);
                }
            }
        }
        throw new RuntimeException("Failed to write to Spanner after " + MAX_RETRIES + " attempts");
    }

    private boolean retryWithDelay(RunnableWithBoolean operation, int maxRetries) {
        int retryCount = 0;
        while (retryCount < maxRetries) {
            if (operation.run()) {
                return true;
            }
            retryCount++;
            logger.warning("Retry attempt " + retryCount + " for operation.");
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
