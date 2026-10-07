package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.shared.infrastructure.batch.CsvFieldNormalizer;
import com.xyzbank.migration.shared.infrastructure.batch.NumberedLineMapper;
import org.springframework.batch.item.file.transform.FieldSet;

public class AnnualMovementLineMapper implements NumberedLineMapper.NumberedFieldSetMapper<AnnualMovementLine> {

    @Override
    public AnnualMovementLine map(FieldSet fieldSet, int lineNumber) {
        return new AnnualMovementLine(
                CsvFieldNormalizer.text(fieldSet.readRawString("cuentaId")),
                CsvFieldNormalizer.text(fieldSet.readRawString("fecha")),
                CsvFieldNormalizer.text(fieldSet.readRawString("transaccion")),
                CsvFieldNormalizer.amount(fieldSet.readRawString("monto")),
                CsvFieldNormalizer.text(fieldSet.readRawString("descripcion")),
                lineNumber
        );
    }
}
