package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.services.MinerScalerService;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Operation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("gcp")
@RequiredArgsConstructor
public class GcpMinerScalerServiceImpl implements MinerScalerService {

    private static final Logger logger = LoggerFactory.getLogger(GcpMinerScalerServiceImpl.class);

    private final Compute compute;
    @Value("${gcp.compute.mig-name}")
    private final String migName;
    @Value("${gcp.project-id}")
    private final String projectId;
    @Value("${gcp.compute.zone}")
    private final String zone;

    @Override
    public void resize(int targetSize) {
        try {
            Compute.InstanceGroupManagers.Resize request =
                    compute.instanceGroupManagers()
                            .resize(projectId, zone, migName, targetSize);
            Operation op = request.execute();
            logger.info("Resize GCP a " + targetSize + ": " + op.getName());
        } catch (Exception e) {
            logger.error("Error GCP resize: " + e.getMessage(), e);
        }
    }
}