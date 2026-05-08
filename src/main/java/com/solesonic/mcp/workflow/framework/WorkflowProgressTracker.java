package com.solesonic.mcp.workflow.framework;

import com.solesonic.mcp.workflow.WeightedProgressCoordinator;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class WorkflowProgressTracker {
    private static final int STARTUP_BAND_START = 1;
    private static final int STARTUP_BAND_END = 9;
    private static final int DYNAMIC_BAND_START = 10;
    private static final int DYNAMIC_BAND_END = 100;

    private final String workflowName;
    private final String correlationId;
    private final WorkflowNotificationService notificationService;
    private final Map<String, Double> normalizedWeights;
    private final ConcurrentHashMap<String, Double> fractions;
    private final AtomicInteger startupPercent;

    public WorkflowProgressTracker(
            String workflowName,
            String correlationId,
            WorkflowNotificationService notificationService,
            Map<String, Double> taskWeights
    ) {
        this.workflowName = Objects.requireNonNull(workflowName, "workflowName must not be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        Objects.requireNonNull(taskWeights, "taskWeights must not be null");

        if (taskWeights.isEmpty()) {
            throw new IllegalArgumentException("taskWeights must not be empty");
        }

        double totalWeight = taskWeights.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        if (totalWeight <= 0.0) {
            throw new IllegalArgumentException("taskWeights total must be positive");
        }

        normalizedWeights = taskWeights.entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> {
                            double weight = entry.getValue();

                            if (weight < 0.0) {
                                throw new IllegalArgumentException("task weight must be non-negative: " + entry.getKey());
                            }

                            return weight / totalWeight;
                        }
                ));

        fractions = new ConcurrentHashMap<>();
        normalizedWeights.keySet().forEach(task -> fractions.put(task, 0.0));
        startupPercent = new AtomicInteger(STARTUP_BAND_START - 1);
    }

    public void startup(String message) {
        int next = startupPercent.updateAndGet(previous -> Math.min(previous + 1, STARTUP_BAND_END));
        notificationService.publish(WorkflowEvent.stepProgress(workflowName, "startup", correlationId, next, message));
    }

    public StepProgress step(String stepName) {
        if (!normalizedWeights.containsKey(stepName)) {
            throw new IllegalArgumentException("Unknown step: " + stepName);
        }

        return new StepProgressImpl(stepName);
    }

    public interface StepProgress extends WeightedProgressCoordinator.TaskProgress {
        void update(double fraction, String message);

        default void done(String message) {
            update(1.0, message);
        }
    }

    private int computeOverallPercent() {
        double weightedProgress = normalizedWeights.entrySet()
                .stream()
                .mapToDouble(entry -> entry.getValue() * fractions.getOrDefault(entry.getKey(), 0.0))
                .sum();

        int dynamicRange = DYNAMIC_BAND_END - DYNAMIC_BAND_START;
        return DYNAMIC_BAND_START + (int) (weightedProgress * dynamicRange);
    }

    private final class StepProgressImpl implements StepProgress {
        private final String stepName;

        private StepProgressImpl(String stepName) {
            this.stepName = stepName;
        }

        @Override
        public void update(double fraction, String message) {
            double clampedFraction = Math.clamp(fraction, 0.0, 1.0);
            fractions.compute(stepName, (_, previous) -> Math.max(previous == null ? 0.0 : previous, clampedFraction));
            int overallPercent = computeOverallPercent();
            notificationService.publish(WorkflowEvent.stepProgress(workflowName, stepName, correlationId, overallPercent, message));
        }
    }
}
