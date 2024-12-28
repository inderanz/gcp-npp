package com.example.reconciliationservice;

public class ReconciliationRequest {
    private String puid;
    private double amount;
    private String sourceService;  // This will hold the name of the calling service

    // Default constructor
    public ReconciliationRequest() {}

    // Parameterized constructor
    public ReconciliationRequest(String puid, double amount, String sourceService) {
        this.puid = puid;
        this.amount = amount;
        this.sourceService = sourceService;
    }

    // Getters and setters
    public String getPuid() {
        return puid;
    }

    public void setPuid(String puid) {
        this.puid = puid;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getSourceService() {
        return sourceService;  // This returns the service name that is calling the reconciliation service
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    @Override
    public String toString() {
        return "ReconciliationRequest{" +
                "puid='" + puid + '\'' +
                ", amount=" + amount +
                ", sourceService='" + sourceService + '\'' +
                '}';
    }
}
