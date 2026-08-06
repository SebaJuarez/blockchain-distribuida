package com.blockchain.miningpool.dtos;

public class MiningResultResponse {
    private String message;
    private double reward;

    public MiningResultResponse() {
    }

    public MiningResultResponse(String message, double reward) {
        this.message = message;
        this.reward = reward;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public double getReward() { return reward; }
    public void setReward(double reward) { this.reward = reward; }
}