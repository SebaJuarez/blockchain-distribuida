package com.blockchain.coordinator.controllers;

import com.blockchain.coordinator.models.Transaction;
import com.blockchain.coordinator.services.TransactionPoolService;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionPoolService transactionPoolService;

    public TransactionController(TransactionPoolService transactionPoolService) {
        this.transactionPoolService = transactionPoolService;
    }

    @PostMapping
    public ResponseEntity<EntityModel<Transaction>> registerTransaction(@RequestBody Transaction transaction) {
        // Se valida que la transacción tenga un ID y un timestamp válido (consistente)
        if (transaction.getId() == null || transaction.getId().isEmpty()) {
            transaction.setId(UUID.randomUUID().toString());
        }
        if (transaction.getTimestamp() == 0) { // Si el timestamp no viene, se asigna el actual
            transaction.setTimestamp(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        }

        transactionPoolService.addTransaction(transaction);

        EntityModel<Transaction> transactionModel = EntityModel.of(transaction,
                linkTo(methodOn(TransactionController.class).registerTransaction(transaction)).withSelfRel(),
                linkTo(methodOn(TransactionController.class).getPendingTransactions()).withRel("all-pending-transactions"));

        return ResponseEntity.created(transactionModel.getRequiredLink("self").toUri()).body(transactionModel);
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
    public ResponseEntity<EntityModel<Integer>> getPendingTransactionCount() {
        int count = transactionPoolService.getPendingTransactionCount();
        EntityModel<Integer> countModel = EntityModel.of(count,
                linkTo(methodOn(TransactionController.class).getPendingTransactionCount()).withSelfRel(),
                linkTo(methodOn(TransactionController.class).getPendingTransactions()).withRel("view-pending-transactions"));
        return ResponseEntity.ok(countModel);
    }
}