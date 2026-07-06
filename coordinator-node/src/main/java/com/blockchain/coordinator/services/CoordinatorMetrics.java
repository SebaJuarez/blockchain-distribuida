package com.blockchain.coordinator.services;

public interface CoordinatorMetrics {
    void incrementTransactionsReceived();
    
    void updatePendingTransactions(int count);
    
    void incrementCandidateBlocksPublished();
    
    void incrementMinerResponses(String outcome);
    
    void recordBlockValidationTime(long durationInMs);
    
    void recordTimeToFirstMinerResponse(long durationInMs);
    
    void incrementBlocksMined();
    
    void updateLastBlockHeight(long height);
    
    void updateDifficulty(String difficulty);
    
    void incrementValidationFailures(String reason);
    
    void incrementPublishRetries();
    
    void recordTransactionValidationTime(long durationInMs);
}