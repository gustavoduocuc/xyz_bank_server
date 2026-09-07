package com.xyzbank.migration.monthlyinterests.infrastructure.batch;

import com.xyzbank.migration.shared.infrastructure.batch.CsvFieldNormalizer;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.lang.NonNull;

public class InterestAccountLineMapper implements FieldSetMapper<InterestAccountLine> {

    @Override
    public InterestAccountLine mapFieldSet(@NonNull FieldSet fieldSet) {
        return new InterestAccountLine(
                CsvFieldNormalizer.text(fieldSet.readRawString("cuentaId")),
                CsvFieldNormalizer.text(fieldSet.readRawString("nombre")),
                CsvFieldNormalizer.amount(fieldSet.readRawString("saldo")),
                CsvFieldNormalizer.integer(fieldSet.readRawString("edad")),
                CsvFieldNormalizer.text(fieldSet.readRawString("tipo"))
        );
    }
}
