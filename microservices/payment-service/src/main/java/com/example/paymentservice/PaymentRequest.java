package com.example.paymentservice;

public class PaymentRequest {
    private String puid;
    private String userId;
    private double amount;
    private String status;  // Add this field

    // Getters and Setters
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

    public String getStatus() {  // Add this getter
        return status;
    }

    public void setStatus(String status) {  // Add this setter
        this.status = status;
    }
}
