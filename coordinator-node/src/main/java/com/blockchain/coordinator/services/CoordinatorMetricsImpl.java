package com.blockchain.coordinator.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;

@Service
public class CoordinatorMetricsImpl implements CoordinatorMetrics {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorMetricsImpl.class);

    private final Counter transactionsReceivedCounter;
    private final AtomicInteger pendingTransactionsGauge;
    private final AtomicLong lastBlockHeightGauge;
    private final AtomicInteger difficultyGauge;
    private final Counter candidateBlocksPublishedCounter;
    private final MeterRegistry meterRegistry;
    private final Timer blockValidationTimer;
    private final Timer timeToFirstMinerResponseTimer;
    private final Counter blocksMinedCounter;
    private final Counter validationFailuresCounter;
    private final Counter publishRetriesCounter;
    private final Timer transactionValidationTimer;
    
    public CoordinatorMetricsImpl(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.transactionsReceivedCounter = Counter.builder("blockchain_transactions_received_total")
                .description("Total de transacciones recibidas por el sistema")
                .register(meterRegistry);
                
        this.pendingTransactionsGauge = new AtomicInteger(0);
        Gauge.builder("blockchain_pending_transactions", pendingTransactionsGauge, AtomicInteger::get)
                .description("Cantidad actual de transacciones pendientes en memoria/mempool")
                .register(meterRegistry);
                
        this.lastBlockHeightGauge = new AtomicLong(0);
        Gauge.builder("blockchain_last_block_height", lastBlockHeightGauge, AtomicLong::get)
                .description("Altura actual de la blockchain")
                .register(meterRegistry);
                
        this.difficultyGauge = new AtomicInteger(0);
        Gauge.builder("blockchain_difficulty", difficultyGauge, AtomicInteger::get)
                .description("Dificultad actual de la red")
                .register(meterRegistry);
                
        this.candidateBlocksPublishedCounter = Counter.builder("blockchain_candidate_blocks_published_total")
                .description("Bloques candidatos enviados a mineros")
                .register(meterRegistry);
                
        this.blockValidationTimer = Timer.builder("blockchain_block_validation_seconds")
                .description("Tiempo que tarda el coordinador en validar un bloque candidato")
                .register(meterRegistry);
                
        this.timeToFirstMinerResponseTimer = Timer.builder("blockchain_time_to_first_miner_response_seconds")
                .description("Tiempo entre publicar un bloque candidato y recibir la primera respuesta de un minero")
                .register(meterRegistry);
                
        this.blocksMinedCounter = Counter.builder("blockchain_blocks_mined_total")
                .description("Bloques finalmente confirmados en la cadena")
                .register(meterRegistry);
                
        this.validationFailuresCounter = Counter.builder("blockchain_validation_failures_total")
                .description("Cantidad de fallos de validación de bloques")
                .register(meterRegistry);
                
        this.publishRetriesCounter = Counter.builder("blockchain_publish_retries_total")
                .description("Reintentos al publicar mensajes en RabbitMQ")
                .register(meterRegistry);
                
        this.transactionValidationTimer = Timer.builder("blockchain_transaction_validation_seconds")
                .description("Tiempo de validación de transacciones individuales")
                .register(meterRegistry);
    }

    @Override
    public void incrementTransactionsReceived() {
        transactionsReceivedCounter.increment();
    }

    @Override
    public void updatePendingTransactions(int count) {
        pendingTransactionsGauge.set(count);
    }

    @Override
    public void incrementCandidateBlocksPublished() {
        candidateBlocksPublishedCounter.increment();
    }

    @Override
    public void incrementMinerResponses(String outcome) {
        Counter.builder("blockchain_miner_responses_total")
                .description("Respuestas de mineros procesadas por el coordinador")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void recordBlockValidationTime(long durationInMs) {
        blockValidationTimer.record(durationInMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordTimeToFirstMinerResponse(long durationInMs) {
        timeToFirstMinerResponseTimer.record(durationInMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void incrementBlocksMined() {
        blocksMinedCounter.increment();
    }

    @Override
    public void updateLastBlockHeight(long height) {
        lastBlockHeightGauge.set(height);
    }

    @Override
    public void updateDifficulty(String difficulty) {
        // Convertimos la dificultad (cadena de ceros) a longitud
        int diffValue = difficulty != null ? difficulty.length() : 0;
        difficultyGauge.set(diffValue);
    }

    @Override
    public void incrementValidationFailures(String reason) {
        Counter.builder("blockchain_validation_failures_total")
                .description("Cantidad de fallos de validación de bloques")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void incrementPublishRetries() {
        publishRetriesCounter.increment();
    }

    @Override
    public void recordTransactionValidationTime(long durationInMs) {
        transactionValidationTimer.record(durationInMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}