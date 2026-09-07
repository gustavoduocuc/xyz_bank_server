package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.annualreports.domain.AnnualMovement;
import com.xyzbank.migration.annualreports.domain.DuplicateMovementDetector;
import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.infrastructure.batch.CsvFieldNormalizer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

public class AnnualMovementProcessor implements ItemProcessor<AnnualMovementLine, AnnualMovement> {

    private final DuplicateMovementDetector duplicateMovementDetector;

    public AnnualMovementProcessor(DuplicateMovementDetector duplicateMovementDetector) {
        this.duplicateMovementDetector = duplicateMovementDetector;
    }

    @Override
    public AnnualMovement process(@NonNull AnnualMovementLine line) {
        Double amount = CsvFieldNormalizer.scaleAmount(line.monto());
        if (amount == null) {
            throw DomainError.validation("Movement amount cannot be empty");
        }

        AnnualMovement movement = AnnualMovement.create(
                CsvFieldNormalizer.text(line.cuentaId()),
                CsvFieldNormalizer.text(line.fecha()),
                CsvFieldNormalizer.text(line.transaccion()),
                amount,
                CsvFieldNormalizer.text(line.descripcion())
        );

        if (duplicateMovementDetector.isDuplicate(movement.businessKey())) {
            throw DomainError.validation("Duplicate annual movement skipped: " + movement.businessKey());
        }

        return movement;
    }
}
