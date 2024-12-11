package com.example.transactionservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @Autowired
    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public String processTransaction(@RequestBody TransactionRequest transactionRequest) {
        transactionService.processTransaction(transactionRequest);
        return "Transaction processed successfully!";
    }
}
