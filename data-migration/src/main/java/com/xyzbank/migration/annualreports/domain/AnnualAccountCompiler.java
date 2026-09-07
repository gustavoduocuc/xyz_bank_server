package com.xyzbank.migration.annualreports.domain;

import com.xyzbank.migration.shared.domain.Id;
import com.xyzbank.migration.shared.domain.Money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class AnnualAccountCompiler {

    private AnnualAccountCompiler() {
    }

    public static List<AnnualAccountSummary> compile(List<AnnualMovement> movements) {
        Map<String, List<AnnualMovement>> movementsByAccount = movements.stream()
                .collect(Collectors.groupingBy(
                        AnnualMovement::accountIdValue,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<AnnualAccountSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<AnnualMovement>> entry : movementsByAccount.entrySet()) {
            summaries.add(summarize(Id.create(entry.getKey()), entry.getValue()));
        }
        return summaries;
    }

    private static AnnualAccountSummary summarize(Id accountId, List<AnnualMovement> movements) {
        Money deposits = Money.zero();
        Money withdrawals = Money.zero();
        Money net = Money.zero();

        for (AnnualMovement movement : movements) {
            net = net.add(movement.amount());
            if (movement.isDeposit()) {
                deposits = deposits.add(movement.amount());
            }
            if (movement.isOutgoing()) {
                withdrawals = withdrawals.add(movement.absoluteAmount());
            }
        }

        return new AnnualAccountSummary(accountId, deposits, withdrawals, net, movements.size());
    }
}
