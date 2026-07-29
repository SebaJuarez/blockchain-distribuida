package com.blockchain.coordinator.controllers;

import com.blockchain.coordinator.dtos.CountResponse;
import com.blockchain.coordinator.dtos.StatusResponse;
import com.blockchain.coordinator.models.Transaction;
import com.blockchain.coordinator.services.TransactionPoolService;
import com.blockchain.coordinator.util.EcUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin("*")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionPoolService transactionPoolService;

    public TransactionController(TransactionPoolService transactionPoolService) {
        this.transactionPoolService = transactionPoolService;
    }

    @PostMapping
    public ResponseEntity<?> registerTransaction(@RequestBody Transaction transaction) {
        if (transaction.getId() == null || transaction.getId().isEmpty()) {
            transaction.setId(UUID.randomUUID().toString());
        }
        if (transaction.getTimestamp() == 0) {
            transaction.setTimestamp(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        }

        if (!isSignatureValid(transaction)) {
            logger.warn("TransactionController: transacción rechazada por firma inválida. sender={}", transaction.getSender());
            return ResponseEntity.badRequest().body(
                    new StatusResponse("Firma inválida, ausente, o sender/receiver malformado. Transacción rechazada."));
        }

        transactionPoolService.addTransaction(transaction);

        EntityModel<Transaction> transactionModel = EntityModel.of(transaction,
                linkTo(methodOn(TransactionController.class).registerTransaction(transaction)).withSelfRel(),
                linkTo(methodOn(TransactionController.class).getPendingTransactions()).withRel("all-pending-transactions"));

        return ResponseEntity.created(transactionModel.getRequiredLink("self").toUri()).body(transactionModel);
    }
    
    private boolean isSignatureValid(Transaction transaction) {
        if (!StringUtils.hasText(transaction.getSender()) || !StringUtils.hasText(transaction.getSignature())) {
            return false;
        }
        try {
            String message = transaction.getReceiver() + "|"
                    + String.format("%.2f", transaction.getAmount()) + "|"
                    + transaction.getTimestamp();

            PublicKey senderKey = EcUtils.decodePublicKeyHex(transaction.getSender());
            Signature verifier = Signature.getInstance("SHA256withECDSA", "BC");
            verifier.initVerify(senderKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Hex.decode(transaction.getSignature()));
        } catch (Exception e) {
            logger.warn("TransactionController: excepción verificando firma: {}", e.getMessage());
            return false;
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<CollectionModel<EntityModel<Transaction>>> getPendingTransactions() {
        List<EntityModel<Transaction>> transactions = transactionPoolService.getAllPendingTransactions().stream()
                .map(transaction -> EntityModel.of(transaction,
                        linkTo(methodOn(TransactionController.class).registerTransaction(transaction)).withRel("add-new-transaction")))
                .collect(Collectors.toList());

        return ResponseEntity.ok(CollectionModel.of(transactions,
                linkTo(methodOn(TransactionController.class).getPendingTransactions()).withSelfRel()));
    }

    @GetMapping("/pending/count")
    public ResponseEntity<EntityModel<CountResponse>> getPendingTransactionCount() {
        int count = transactionPoolService.getPendingTransactionCount();
        CountResponse countResponse = new CountResponse(count);

        EntityModel<CountResponse> countModel = EntityModel.of(countResponse,
                linkTo(methodOn(TransactionController.class).getPendingTransactionCount()).withSelfRel(),
                linkTo(methodOn(TransactionController.class).getPendingTransactions()).withRel("view-pending-transactions"));
        return ResponseEntity.ok(countModel);
    }
}