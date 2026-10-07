package com.xyzbank.migration.shared.infrastructure.batch;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * A single-shot tasklet that runs one idempotent action, such as publishing staged
 * rows or rebuilding a projection, inside the step's transaction.
 */
public class ActionTasklet implements Tasklet {

    private final Runnable action;

    public ActionTasklet(Runnable action) {
        this.action = action;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        action.run();
        return RepeatStatus.FINISHED;
    }
}
