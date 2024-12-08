package com.example.reconciliationservice;

public class ReconciliationRequest {
    private String puid;
    private String status;

    // Getters and Setters
    public String getPuid() {
        return puid;
    }

    public void setPuid(String puid) {
        this.puid = puid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
