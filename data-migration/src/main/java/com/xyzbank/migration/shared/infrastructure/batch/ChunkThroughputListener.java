package com.xyzbank.migration.shared.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.lang.NonNull;

public class ChunkThroughputListener implements ChunkListener {

    private static final Logger logger = LoggerFactory.getLogger(ChunkThroughputListener.class);

    @Override
    public void beforeChunk(@NonNull ChunkContext context) {
        logger.debug(
                "Chunk starting step={} thread={}",
                context.getStepContext().getStepName(),
                Thread.currentThread().getName()
        );
    }

    @Override
    public void afterChunk(@NonNull ChunkContext context) {
        logger.debug(
                "Chunk completed step={} thread={}",
                context.getStepContext().getStepName(),
                Thread.currentThread().getName()
        );
    }

    @Override
    public void afterChunkError(@NonNull ChunkContext context) {
        logger.warn(
                "Chunk error step={} thread={}",
                context.getStepContext().getStepName(),
                Thread.currentThread().getName()
        );
    }
}
