package com.example.transactionservice;

public class TransactionRequest {
    private String transactionId;
    private String userId;
    private double amount;

    // Default constructor
    public TransactionRequest() {}

    // Parameterized constructor
    public TransactionRequest(String transactionId, String userId, double amount) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
    }

    // Getters
    public String getTransactionId() {
        return transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public double getAmount() {
        return amount;
    }

    // Setters
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "TransactionRequest{" +
                "transactionId='" + transactionId + '\'' +
                ", userId='" + userId + '\'' +
                ", amount=" + amount +
                '}';
    }
}
