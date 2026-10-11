package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.shared.infrastructure.batch.CsvFieldNormalizer;
import com.xyzbank.migration.shared.infrastructure.batch.NumberedLineMapper;
import org.springframework.batch.item.file.transform.FieldSet;

public class DailyTransactionLineMapper implements NumberedLineMapper.NumberedFieldSetMapper<DailyTransactionLine> {

    @Override
    public DailyTransactionLine map(FieldSet fieldSet, int lineNumber) {
        return new DailyTransactionLine(
                CsvFieldNormalizer.text(fieldSet.readRawString("id")),
                CsvFieldNormalizer.text(fieldSet.readRawString("fecha")),
                CsvFieldNormalizer.amount(fieldSet.readRawString("monto")),
                CsvFieldNormalizer.text(fieldSet.readRawString("tipo")),
                lineNumber
        );
    }
}
