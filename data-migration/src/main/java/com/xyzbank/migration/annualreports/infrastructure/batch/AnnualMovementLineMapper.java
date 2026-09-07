package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.shared.infrastructure.batch.CsvFieldNormalizer;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.lang.NonNull;

public class AnnualMovementLineMapper implements FieldSetMapper<AnnualMovementLine> {

    @Override
    public AnnualMovementLine mapFieldSet(@NonNull FieldSet fieldSet) {
        return new AnnualMovementLine(
                CsvFieldNormalizer.text(fieldSet.readRawString("cuentaId")),
                CsvFieldNormalizer.text(fieldSet.readRawString("fecha")),
                CsvFieldNormalizer.text(fieldSet.readRawString("transaccion")),
                CsvFieldNormalizer.amount(fieldSet.readRawString("monto")),
                CsvFieldNormalizer.text(fieldSet.readRawString("descripcion"))
        );
    }
}
