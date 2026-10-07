package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.annualreports.application.ports.InMemoryAnnualMovementStore;
import com.xyzbank.migration.annualreports.domain.AnnualMovement;
import com.xyzbank.migration.shared.domain.SourceLine;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TheAnnualMovementItemWriterTest {

    /*
     * Cases:
     * 1. Stores every movement of the chunk
     */

    @Nested
    class TheAnnualMovementItemWriter {

        @Test
        void storesEveryMovementOfTheChunk() {
            InMemoryAnnualMovementStore store = new InMemoryAnnualMovementStore();
            AnnualMovement deposit = AnnualMovement.create("101", "2024-01-01", "deposito", 1000, "Ingreso", SourceLine.of(2));
            AnnualMovement withdrawal = AnnualMovement.create("101", "2024-03-15", "retiro", -500, "Retiro", SourceLine.of(3));

            new AnnualMovementItemWriter(store).write(new Chunk<>(List.of(deposit, withdrawal)));

            assertEquals(List.of(deposit, withdrawal), store.written());
        }
    }
}
