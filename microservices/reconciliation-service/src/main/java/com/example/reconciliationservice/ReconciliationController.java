package com.example.reconciliationservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @Autowired
    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping
    public String processReconciliation(@RequestBody ReconciliationRequest reconciliationRequest) {
        reconciliationService.processReconciliation(reconciliationRequest);
        return "Reconciliation processed successfully!";
    }

    // Add endpoint to fetch payment state
    @GetMapping("/payment-state/{puid}")
    public String getPaymentState(@PathVariable String puid) {
        return reconciliationService.fetchPaymentState(puid);
    }
}
