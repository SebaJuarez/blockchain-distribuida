package com.blockchain.miningpool.services;

import com.blockchain.miningpool.models.Block;

public interface WorkerDispatcher {

    void dispatchSubTasks(Block block, String challenge, long from, long to);
    void broadcastCancel(String preliminaryHash)
}