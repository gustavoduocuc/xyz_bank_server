package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.annualreports.application.ports.AnnualMovementStore;
import com.xyzbank.migration.annualreports.domain.AnnualMovement;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import java.util.ArrayList;

public class AnnualMovementItemWriter implements ItemWriter<AnnualMovement> {

    private final AnnualMovementStore annualMovementStore;

    public AnnualMovementItemWriter(AnnualMovementStore annualMovementStore) {
        this.annualMovementStore = annualMovementStore;
    }

    @Override
    public void write(@NonNull Chunk<? extends AnnualMovement> chunk) {
        annualMovementStore.write(new ArrayList<>(chunk.getItems()));
    }
}
