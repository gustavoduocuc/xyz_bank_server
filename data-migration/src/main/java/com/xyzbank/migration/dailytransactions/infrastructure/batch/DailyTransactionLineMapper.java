package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.shared.infrastructure.batch.CsvFieldNormalizer;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.lang.NonNull;

public class DailyTransactionLineMapper implements FieldSetMapper<DailyTransactionLine> {

    @Override
    public DailyTransactionLine mapFieldSet(@NonNull FieldSet fieldSet) {
        return new DailyTransactionLine(
                CsvFieldNormalizer.text(fieldSet.readRawString("id")),
                CsvFieldNormalizer.text(fieldSet.readRawString("fecha")),
                CsvFieldNormalizer.amount(fieldSet.readRawString("monto")),
                CsvFieldNormalizer.text(fieldSet.readRawString("tipo"))
        );
    }
}
