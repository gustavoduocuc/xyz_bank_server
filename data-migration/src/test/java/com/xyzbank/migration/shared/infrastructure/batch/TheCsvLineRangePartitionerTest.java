package com.xyzbank.migration.shared.infrastructure.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TheCsvLineRangePartitionerTest {

    /*
     * Cases:
     * 1. Splits 1000 data rows into 4 contiguous ranges that cover every row once
     * 2. Puts the remainder of an uneven split in the last range
     * 3. Creates one range per row when there are fewer rows than the grid size
     * 4. Creates no range for a header-only file
     */

    @Nested
    class TheCsvLineRangePartitioner {

        @TempDir
        Path directory;

        @Test
        void splits1000DataRowsInto4ContiguousRangesThatCoverEveryRowOnce() throws IOException {
            Map<String, ExecutionContext> partitions = partitionerFor(1000).partition(4);

            assertEquals(List.of("0-250", "250-500", "500-750", "750-1000"), ranges(partitions));
        }

        @Test
        void putsTheRemainderOfAnUnevenSplitInTheLastRange() throws IOException {
            Map<String, ExecutionContext> partitions = partitionerFor(10).partition(3);

            assertEquals(List.of("0-3", "3-6", "6-10"), ranges(partitions));
        }

        @Test
        void createsOneRangePerRowWhenThereAreFewerRowsThanTheGridSize() throws IOException {
            Map<String, ExecutionContext> partitions = partitionerFor(3).partition(8);

            assertEquals(List.of("0-1", "1-2", "2-3"), ranges(partitions));
        }

        @Test
        void createsNoRangeForAHeaderOnlyFile() throws IOException {
            Map<String, ExecutionContext> partitions = partitionerFor(0).partition(4);

            assertTrue(partitions.isEmpty());
        }

        private CsvLineRangePartitioner partitionerFor(int dataRows) throws IOException {
            List<String> lines = new ArrayList<>();
            lines.add("id,fecha,monto,tipo");
            for (int row = 1; row <= dataRows; row++) {
                lines.add(row + ",2024-01-01,100,debito");
            }
            Path file = Files.write(directory.resolve("rows.csv"), lines);
            return new CsvLineRangePartitioner(new FileSystemResource(file), 1);
        }

        private List<String> ranges(Map<String, ExecutionContext> partitions) {
            return partitions.values().stream()
                    .map(context -> context.getInt(CsvLineRangePartitioner.startItem)
                            + "-" + context.getInt(CsvLineRangePartitioner.endItem))
                    .sorted((left, right) -> Integer.compare(
                            Integer.parseInt(left.split("-")[0]), Integer.parseInt(right.split("-")[0])))
                    .toList();
        }
    }
}
