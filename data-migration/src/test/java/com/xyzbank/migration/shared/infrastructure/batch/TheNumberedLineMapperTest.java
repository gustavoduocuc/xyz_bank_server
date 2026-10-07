package com.xyzbank.migration.shared.infrastructure.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TheNumberedLineMapperTest {

    /*
     * Cases:
     * 1. Passes the line number to the mapper together with the tokenized fields
     * 2. Numbers data lines by their position in the file, header included
     */

    record Row(String name, int line) {
    }

    @Nested
    class TheNumberedLineMapper {

        private final NumberedLineMapper<Row> mapper = new NumberedLineMapper<>(
                tokenizer(),
                (fieldSet, lineNumber) -> new Row(fieldSet.readRawString("name"), lineNumber)
        );

        @Test
        void passesTheLineNumberToTheMapperTogetherWithTheTokenizedFields() throws Exception {
            Row row = mapper.mapLine("alice,1", 7);

            assertEquals(new Row("alice", 7), row);
        }

        @Test
        void numbersDataLinesByTheirPositionInTheFileHeaderIncluded() throws Exception {
            FlatFileItemReader<Row> reader = new FlatFileItemReaderBuilder<Row>()
                    .name("rows")
                    .resource(new ByteArrayResource("name,id\nalice,1\nbob,2\n".getBytes(StandardCharsets.UTF_8)))
                    .linesToSkip(1)
                    .lineMapper(mapper)
                    .build();
            reader.open(new ExecutionContext());

            Row first = reader.read();
            Row second = reader.read();
            reader.close();

            assertEquals(new Row("alice", 2), first);
            assertEquals(new Row("bob", 3), second);
        }

        private static DelimitedLineTokenizer tokenizer() {
            DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
            tokenizer.setNames("name", "id");
            return tokenizer;
        }
    }
}
