package com.psredemobank.payments.service;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.Timestamp;
import com.psredemobank.payments.model.PaymentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Collections;
import java.util.logging.Logger;

@Service
public class PaymentService {

    private static final Logger logger = Logger.getLogger(PaymentService.class.getName());
    private DatabaseClient spannerClient;

    @Value("${spanner.projectId}")
    private String spannerProjectId;

    @Value("${spanner.instanceId}")
    private String spannerInstanceId;

    @Value("${spanner.databaseName}")
    private String spannerDatabaseName;

    @PostConstruct
    public void initializeSpannerClient() {
        long startTime = System.currentTimeMillis();
        try {
            logger.info("Initializing Spanner client...");
            logger.info("Environment: " + System.getenv("ENVIRONMENT")); // Log environment (optional, if set)
            logger.info("Spanner Project ID: " + spannerProjectId);
            logger.info("Spanner Instance ID: " + spannerInstanceId);
            logger.info("Spanner Database Name: " + spannerDatabaseName);

            SpannerOptions options = SpannerOptions.newBuilder()
                    .setProjectId(spannerProjectId)
                    .build();
            Spanner spanner = options.getService();

            // Log the service account being used
            String serviceAccount = options.getCredentials().getAuthenticationType().toString();
            logger.info("Using Service Account: " + serviceAccount);

            // Get Kubernetes service account name if running in a GKE environment
            String kubeServiceAccount = System.getenv("KUBERNETES_SERVICE_ACCOUNT");
            if (kubeServiceAccount != null) {
                logger.info("Kubernetes Service Account (KSA): " + kubeServiceAccount);
            } else {
                logger.info("Kubernetes Service Account (KSA): Not set or not running in Kubernetes.");
            }

            DatabaseId dbId = DatabaseId.of(spannerProjectId, spannerInstanceId, spannerDatabaseName);
            this.spannerClient = spanner.getDatabaseClient(dbId);

            long endTime = System.currentTimeMillis();
            logger.info("Successfully initialized Spanner client.");
            logger.info("Initialization completed in " + (endTime - startTime) + " ms.");
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            logger.severe("Failed to initialize Spanner client!");
            logger.severe("Spanner Project ID: " + spannerProjectId);
            logger.severe("Spanner Instance ID: " + spannerInstanceId);
            logger.severe("Spanner Database Name: " + spannerDatabaseName);
            logger.severe("Time taken to attempt initialization: " + (endTime - startTime) + " ms.");
            logger.severe("Error: " + e.getMessage());
            logger.severe("Stack Trace:");
            e.printStackTrace();

            logger.severe("Possible Causes:");
            logger.severe("1. Verify if the Spanner instance and database exist.");
            logger.severe("2. Ensure the service account (" + 
                          (System.getenv("KUBERNETES_SERVICE_ACCOUNT") != null ? System.getenv("KUBERNETES_SERVICE_ACCOUNT") : "Unknown") + 
                          ") has the required IAM permissions: roles/spanner.databaseUser or custom role assigned.");
            logger.severe("3. Confirm that the application is running in the correct GCP project.");
            logger.severe("4. If using Workload Identity, ensure the Kubernetes Service Account is mapped to the GCP Service Account.");
            logger.severe("5. Check network connectivity and firewall rules if applicable.");

            System.err.println("Shutting down application due to Spanner connectivity issue...");
            System.exit(1); // Exit with a non-zero status
        }
    }

    public void processPayment(PaymentRequest paymentRequest) {
        String status = "PENDING"; // Default status
        Instant timestamp = Instant.now();

        try {
            // Use fully qualified name for Timestamp
            Timestamp spannerTimestamp = Timestamp.ofTimeSecondsAndNanos(timestamp.getEpochSecond(), timestamp.getNano());  // Corrected line

            Mutation insertMutation = Mutation.newInsertBuilder("Payments")
                    .set("PaymentUID").to(paymentRequest.getPaymentUid())
                    .set("UserId").to(paymentRequest.getUserId())
                    .set("Amount").to(paymentRequest.getAmount())
                    .set("Status").to(status)
                    .set("Timestamp").to(spannerTimestamp)
                    .build();

            spannerClient.write(Collections.singletonList(insertMutation));
            status = "COMPLETED";
            logger.info("Payment processed successfully for PaymentUID: " + paymentRequest.getPaymentUid());
        } catch (Exception e) {
            status = "FAILED";
            logger.severe("Payment failed for PaymentUID " + paymentRequest.getPaymentUid() + ": " + e.getMessage());
        }

        try {
            Mutation updateStatusMutation = Mutation.newUpdateBuilder("Payments")
                    .set("PaymentUID").to(paymentRequest.getPaymentUid())
                    .set("Status").to(status)
                    .build();

            spannerClient.write(Collections.singletonList(updateStatusMutation));
            logger.info("Payment status updated to '" + status + "' for PaymentUID: " + paymentRequest.getPaymentUid());
        } catch (Exception e) {
            logger.severe("Failed to update status for PaymentUID " + paymentRequest.getPaymentUid() + ": " + e.getMessage());
        }
    }
}
