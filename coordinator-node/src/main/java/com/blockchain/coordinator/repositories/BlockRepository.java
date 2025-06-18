package com.blockchain.coordinator.repositories;

import com.blockchain.coordinator.models.Block;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlockRepository extends CrudRepository<Block, String> {}