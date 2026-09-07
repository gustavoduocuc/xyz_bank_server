package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.annualreports.application.ports.AnnualAuditWriter;
import com.xyzbank.migration.annualreports.domain.AnnualAccountCompiler;
import com.xyzbank.migration.annualreports.domain.AnnualMovement;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

public class AnnualAuditItemWriter implements ItemWriter<AnnualMovement>, ItemStream {

    private final AnnualAuditWriter annualAuditWriter;
    private final List<AnnualMovement> bufferedMovements = new ArrayList<>();
    private final Object bufferLock = new Object();

    public AnnualAuditItemWriter(AnnualAuditWriter annualAuditWriter) {
        this.annualAuditWriter = annualAuditWriter;
    }

    @Override
    public void write(@NonNull Chunk<? extends AnnualMovement> chunk) {
        synchronized (bufferLock) {
            bufferedMovements.addAll(chunk.getItems());
        }
    }

    @Override
    public void open(@NonNull ExecutionContext executionContext) throws ItemStreamException {
    }

    @Override
    public void update(@NonNull ExecutionContext executionContext) throws ItemStreamException {
    }

    @Override
    public void close() throws ItemStreamException {
        List<AnnualMovement> snapshot;
        synchronized (bufferLock) {
            if (bufferedMovements.isEmpty()) {
                return;
            }
            snapshot = List.copyOf(bufferedMovements);
            bufferedMovements.clear();
        }
        annualAuditWriter.write(AnnualAccountCompiler.compile(snapshot));
    }
}
