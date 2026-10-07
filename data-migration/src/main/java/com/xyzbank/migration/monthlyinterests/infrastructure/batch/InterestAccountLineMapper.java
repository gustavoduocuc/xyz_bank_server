package com.xyzbank.migration.monthlyinterests.infrastructure.batch;

import com.xyzbank.migration.shared.infrastructure.batch.CsvFieldNormalizer;
import com.xyzbank.migration.shared.infrastructure.batch.NumberedLineMapper;
import org.springframework.batch.item.file.transform.FieldSet;

public class InterestAccountLineMapper implements NumberedLineMapper.NumberedFieldSetMapper<InterestAccountLine> {

    @Override
    public InterestAccountLine map(FieldSet fieldSet, int lineNumber) {
        return new InterestAccountLine(
                CsvFieldNormalizer.text(fieldSet.readRawString("cuentaId")),
                CsvFieldNormalizer.text(fieldSet.readRawString("nombre")),
                CsvFieldNormalizer.amount(fieldSet.readRawString("saldo")),
                CsvFieldNormalizer.integer(fieldSet.readRawString("edad")),
                CsvFieldNormalizer.text(fieldSet.readRawString("tipo")),
                lineNumber
        );
    }
}
