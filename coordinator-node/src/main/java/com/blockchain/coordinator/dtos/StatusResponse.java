package com.blockchain.coordinator.dtos;

public class StatusResponse {
    private String message;

    public StatusResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
