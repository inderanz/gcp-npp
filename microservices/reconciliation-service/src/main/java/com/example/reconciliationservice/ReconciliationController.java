package com.example.reconciliationservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    @Autowired
    private ReconciliationService reconciliationService;

    @PostMapping
    public ResponseEntity<String> processReconciliation(@RequestBody ReconciliationRequest reconciliationRequest) {
        try {
            reconciliationService.processReconciliation(reconciliationRequest);
            return ResponseEntity.ok("Reconciliation processed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to process reconciliation");
        }
    }
}
