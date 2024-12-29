package com.example.transactionservice;

public class TransactionRequest {
    private String puid;  // PUID used as the unique identifier
    private String userId;
    private double amount;

    // Default constructor
    public TransactionRequest() {}

    // Parameterized constructor
    public TransactionRequest(String puid, String userId, double amount) {
        this.puid = puid;  // Set PUID instead of TransactionID
        this.userId = userId;
        this.amount = amount;
    }

    // Getters and setters
    public String getPuid() {
        return puid;
    }

    public void setPuid(String puid) {
        this.puid = puid;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "TransactionRequest{" +
                "puid='" + puid + '\'' +
                ", userId='" + userId + '\'' +
                ", amount=" + amount +
                '}';
    }
}
