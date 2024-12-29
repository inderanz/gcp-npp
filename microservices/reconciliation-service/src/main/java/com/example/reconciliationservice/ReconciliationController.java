package com.example.reconciliationservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.logging.Logger;

@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    private static final Logger logger = Logger.getLogger(ReconciliationController.class.getName());

    @Autowired
    private ReconciliationService reconciliationService;

    @PostMapping
    public ResponseEntity<String> processReconciliation(@RequestBody ReconciliationRequest reconciliationRequest) {
        logger.info("Received reconciliation request: " + reconciliationRequest);
        if (reconciliationRequest.getPuid() == null || reconciliationRequest.getAmount() <= 0) {
            return ResponseEntity.badRequest().body("Invalid reconciliation request");
        }

        try {
            reconciliationService.processReconciliation(reconciliationRequest);
            return ResponseEntity.ok("Reconciliation processed successfully.");
        } catch (Exception e) {
            logger.severe("Error processing reconciliation: " + e.getMessage());
            return ResponseEntity.status(500).body("Error processing reconciliation: " + e.getMessage());
        }
    }
}
