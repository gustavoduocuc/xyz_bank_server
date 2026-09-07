package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.dailytransactions.application.ports.DailyReportWriter;
import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

public class DailyReportItemWriter implements ItemWriter<ProcessedTransaction> {

    private final DailyReportWriter dailyReportWriter;

    public DailyReportItemWriter(DailyReportWriter dailyReportWriter) {
        this.dailyReportWriter = dailyReportWriter;
    }

    @Override
    public void write(@NonNull Chunk<? extends ProcessedTransaction> chunk) {
        List<ProcessedTransaction> transactions = new ArrayList<>(chunk.getItems());
        dailyReportWriter.write(transactions);
    }
}
