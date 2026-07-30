package com.blockchain.miningpool.repositories;

import com.blockchain.miningpool.models.PoolBalance;
import org.springframework.data.repository.CrudRepository;

public interface PoolBalanceRepository extends CrudRepository<PoolBalance, String> {}