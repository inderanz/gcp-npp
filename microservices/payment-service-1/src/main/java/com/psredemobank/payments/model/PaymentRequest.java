package com.psredemobank.payments.model;

public class PaymentRequest {
    private String paymentUid;
    private String userId;
    private double amount;
    private String status;

    // Getters and Setters
    public String getPaymentUid() {
        return paymentUid;
    }

    public void setPaymentUid(String paymentUid) {
        this.paymentUid = paymentUid;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
