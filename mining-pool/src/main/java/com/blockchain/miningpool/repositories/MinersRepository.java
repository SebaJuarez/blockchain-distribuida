package com.blockchain.miningpool.repositories;


import com.blockchain.miningpool.models.Miner;
import org.springframework.data.repository.CrudRepository;

public interface MinersRepository extends CrudRepository<Miner, String> {}