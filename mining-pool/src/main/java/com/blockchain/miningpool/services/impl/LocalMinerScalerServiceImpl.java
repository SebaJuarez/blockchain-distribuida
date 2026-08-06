package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.services.MinerScalerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalMinerScalerServiceImpl implements MinerScalerService {

    private static final Logger logger = LoggerFactory.getLogger(LocalMinerScalerServiceImpl.class);

    @Override
    public void resize(int targetSize) {
        logger.info("[LOCAL] Simulando resize del MIG a " + targetSize);
    }
}