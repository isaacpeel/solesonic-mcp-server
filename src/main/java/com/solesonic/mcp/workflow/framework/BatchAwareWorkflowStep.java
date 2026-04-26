package com.solesonic.mcp.workflow.framework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface BatchAwareWorkflowStep<C extends WorkflowContext> extends WorkflowStep<C> {
    Logger log = LoggerFactory.getLogger(BatchAwareWorkflowStep.class);

    WorkflowDecision executeBatch(C context, WorkflowExecutionContext executionContext, int startAt, int batchSize);

    boolean requiresBatching(C context);

    int totalItemCount(C context);

    int preferredBatchSize();

    @Override
    default WorkflowDecision execute(C context, WorkflowExecutionContext executionContext) {
        if (!requiresBatching(context)) {
            return executeBatch(context, executionContext, 0, totalItemCount(context));
        }

        int totalItems = totalItemCount(context);
        int batchSize = preferredBatchSize();
        int totalBatches = (int) Math.ceil((double) totalItems / batchSize);

        log.info("Step '{}' executing in {} batches of {} (total items: {})", name(), totalBatches, batchSize, totalItems);

        WorkflowDecision lastDecision = WorkflowDecision.continueWorkflow();

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int batchStartAt = batchIndex * batchSize;
            int currentBatchNumber = batchIndex + 1;

            executionContext.progressTracker().step(name()).update(
                    (double) currentBatchNumber / totalBatches,
                    "Processing batch %d of %d".formatted(currentBatchNumber, totalBatches)
            );

            lastDecision = executeBatch(context, executionContext, batchStartAt, batchSize);

            if (lastDecision.outcome() != WorkflowOutcome.COMPLETED) {
                break;
            }
        }

        if (lastDecision.outcome() == WorkflowOutcome.COMPLETED) {
            executionContext.progressTracker().step(name()).done(
                    "All %d batches completed".formatted(totalBatches)
            );
        }

        return lastDecision;
    }
}
