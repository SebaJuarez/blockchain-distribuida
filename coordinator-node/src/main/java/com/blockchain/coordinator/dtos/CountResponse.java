package com.blockchain.coordinator.dtos;

public class CountResponse {
    private int count;

    public CountResponse(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}