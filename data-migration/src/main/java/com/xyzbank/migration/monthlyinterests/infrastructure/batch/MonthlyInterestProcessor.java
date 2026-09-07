package com.xyzbank.migration.monthlyinterests.infrastructure.batch;

import com.xyzbank.migration.monthlyinterests.domain.Account;
import com.xyzbank.migration.monthlyinterests.domain.DuplicateAccountDetector;
import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;
import com.xyzbank.migration.monthlyinterests.domain.InterestRatePolicy;
import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.infrastructure.batch.CsvFieldNormalizer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

public class MonthlyInterestProcessor implements ItemProcessor<InterestAccountLine, InterestApplied> {

    private final DuplicateAccountDetector duplicateAccountDetector;

    public MonthlyInterestProcessor(DuplicateAccountDetector duplicateAccountDetector) {
        this.duplicateAccountDetector = duplicateAccountDetector;
    }

    @Override
    public InterestApplied process(@NonNull InterestAccountLine line) {
        Double balance = CsvFieldNormalizer.scaleAmount(line.saldo());
        if (balance == null) {
            throw DomainError.validation("Account balance cannot be empty");
        }
        if (line.edad() == null) {
            throw DomainError.validation("Account age cannot be empty");
        }

        Account account = Account.create(
                CsvFieldNormalizer.text(line.cuentaId()),
                CsvFieldNormalizer.text(line.nombre()),
                balance,
                line.edad(),
                CsvFieldNormalizer.text(line.tipo())
        );

        if (duplicateAccountDetector.isDuplicate(account.idValue())) {
            throw DomainError.validation("Duplicate account skipped: " + account.idValue());
        }

        return InterestRatePolicy.apply(account);
    }
}
