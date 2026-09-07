package cl.duoc.xyzbank.coreservice.transactions.application.usecases;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.transactions.application.dto.TransactionDetailResponse;

public class GetTransactionDetailUseCase {

    private final TransactionRepository transactionRepository;

    public GetTransactionDetailUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionDetailResponse execute(String transactionId) {
        Id id = Id.create(transactionId);
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Transaction " + transactionId + " not found"));

        return toResponse(transaction);
    }

    private TransactionDetailResponse toResponse(Transaction transaction) {
        return new TransactionDetailResponse(
                transaction.getId().getValue(),
                transaction.getAccountId().getValue(),
                transaction.getType().name(),
                transaction.getAmount().getAmount(),
                transaction.getAmount().getCurrency(),
                transaction.getOccurredOn().toString(),
                transaction.getDescription());
    }
}
