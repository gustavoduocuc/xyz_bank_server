package com.xyzbank.migration.shared.infrastructure.batch;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Splits a CSV into contiguous ranges of data rows. Each range is the half-open
 * interval of item counts (startItem, endItem] that a FlatFileItemReader reads with
 * {@code currentItemCount = startItem} and {@code maxItemCount = endItem}. Partition
 * names are stable, so a restart re-creates only the ranges that did not complete.
 */
public class CsvLineRangePartitioner implements Partitioner {

    public static final String startItem = "startItem";
    public static final String endItem = "endItem";

    private final Resource resource;
    private final int headerLines;

    public CsvLineRangePartitioner(Resource resource, int headerLines) {
        this.resource = resource;
        this.headerLines = headerLines;
    }

    @Override
    @NonNull
    public Map<String, ExecutionContext> partition(int gridSize) {
        int dataRows = countDataRows();
        int ranges = Math.min(Math.max(gridSize, 1), dataRows);
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
        if (ranges == 0) {
            return partitions;
        }

        int rangeSize = dataRows / ranges;
        for (int index = 0; index < ranges; index++) {
            int start = index * rangeSize;
            int end = index == ranges - 1 ? dataRows : start + rangeSize;
            ExecutionContext context = new ExecutionContext();
            context.putInt(startItem, start);
            context.putInt(endItem, end);
            partitions.put("range" + index, context);
        }
        return partitions;
    }

    private int countDataRows() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            long lines = reader.lines().count();
            return (int) Math.max(lines - headerLines, 0);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot count the rows of " + resource.getDescription(), exception);
        }
    }
}
