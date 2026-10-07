package com.xyzbank.migration.shared.infrastructure.batch;

import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.batch.item.file.transform.LineTokenizer;
import org.springframework.lang.NonNull;

/**
 * Like {@code DefaultLineMapper}, but hands the file line number to the mapper so
 * each record can carry the line it was read from.
 */
public class NumberedLineMapper<T> implements LineMapper<T> {

    @FunctionalInterface
    public interface NumberedFieldSetMapper<T> {

        T map(FieldSet fieldSet, int lineNumber);
    }

    private final LineTokenizer tokenizer;
    private final NumberedFieldSetMapper<T> fieldSetMapper;

    public NumberedLineMapper(LineTokenizer tokenizer, NumberedFieldSetMapper<T> fieldSetMapper) {
        this.tokenizer = tokenizer;
        this.fieldSetMapper = fieldSetMapper;
    }

    public static <T> NumberedLineMapper<T> delimited(NumberedFieldSetMapper<T> fieldSetMapper, String... names) {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames(names);
        return new NumberedLineMapper<>(tokenizer, fieldSetMapper);
    }

    @Override
    @NonNull
    public T mapLine(@NonNull String line, int lineNumber) {
        return fieldSetMapper.map(tokenizer.tokenize(line), lineNumber);
    }
}
