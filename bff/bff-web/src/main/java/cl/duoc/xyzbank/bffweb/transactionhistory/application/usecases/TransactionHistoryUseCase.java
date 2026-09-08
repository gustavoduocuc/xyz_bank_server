package cl.duoc.xyzbank.bffweb.transactionhistory.application.usecases;

import cl.duoc.xyzbank.bffweb.shared.application.RequestRejectedException;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.dto.TransactionHistoryResponse;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.ports.TransactionsPort;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class TransactionHistoryUseCase {

    private static final String DEBIT = "DEBIT";
    private static final String CREDIT = "CREDIT";

    private final TransactionsPort transactionsPort;

    public TransactionHistoryUseCase(TransactionsPort transactionsPort) {
        this.transactionsPort = transactionsPort;
    }

    public TransactionHistoryResponse execute(
            String accountId, String from, String to, String type, String cursor, Integer pageSize) {
        validateDateRange(from, to);
        validateType(type);
        return transactionsPort.fetchHistory(accountId, from, to, type, cursor, pageSize);
    }

    private void validateDateRange(String from, String to) {
        Optional<LocalDate> fromDate = parseDate(from);
        Optional<LocalDate> toDate = parseDate(to);
        if (fromDate.isPresent() && toDate.isPresent() && fromDate.get().isAfter(toDate.get())) {
            throw RequestRejectedException.validation("From date cannot be after to date");
        }
    }

    private Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException exception) {
            throw RequestRejectedException.validation("Date must be in yyyy-MM-dd format");
        }
    }

    private void validateType(String type) {
        if (type == null || type.isBlank()) {
            return;
        }
        if (!DEBIT.equals(type) && !CREDIT.equals(type)) {
            throw RequestRejectedException.validation("Type must be DEBIT or CREDIT");
        }
    }
}
