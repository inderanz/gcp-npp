package com.example.reconciliationservice;

public class PaymentState {
    private String puid;
    private String history;

    // Default constructor
    public PaymentState() {}

    // Parameterized constructor
    public PaymentState(String puid, String history) {
        this.puid = puid;
        this.history = history;
    }

    // Getters
    public String getPuid() {
        return puid;
    }

    public String getHistory() {
        return history;
    }

    // Setters
    public void setPuid(String puid) {
        this.puid = puid;
    }

    public void setHistory(String history) {
        this.history = history;
    }

    @Override
    public String toString() {
        return "PaymentState{" +
                "puid='" + puid + '\'' +
                ", history='" + history + '\'' +
                '}';
    }
}
