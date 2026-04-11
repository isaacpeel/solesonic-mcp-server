package com.solesonic.mcp.workflow;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class WeightedProgressCoordinator {
    private static final int STARTUP_BAND_START = 1;
    private static final int STARTUP_BAND_END = 9;
    private static final int DYNAMIC_BAND_START = 10;
    private static final int DYNAMIC_BAND_END = 100;

    private final ProgressReporter progressReporter;
    private final Map<String, Double> normalizedWeights;
    private final ConcurrentHashMap<String, Double> fractions;
    private final AtomicInteger startupPercent;

    public WeightedProgressCoordinator(ProgressReporter progressReporter, Map<String, Double> taskWeights) {
        this.progressReporter = Objects.requireNonNull(progressReporter, "progressReporter must not be null");
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

        this.normalizedWeights = taskWeights.entrySet()
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

        this.fractions = new ConcurrentHashMap<>();
        this.normalizedWeights.keySet().forEach(task -> this.fractions.put(task, 0.0));
        this.startupPercent = new AtomicInteger(STARTUP_BAND_START - 1);
    }

    public TaskProgress task(String taskName) {
        if (!normalizedWeights.containsKey(taskName)) {
            throw new IllegalArgumentException("Unknown task: " + taskName);
        }
        return new TaskProgressImpl(taskName);
    }

    public void startup(String message) {
        int next = startupPercent.updateAndGet(previous -> Math.min(previous + 1, STARTUP_BAND_END));
        progressReporter.emit(next, message);
    }

    public interface TaskProgress {
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
        return DYNAMIC_BAND_START + (int) Math.round(weightedProgress * dynamicRange);
    }

    private final class TaskProgressImpl implements TaskProgress {
        private final String taskName;

        private TaskProgressImpl(String taskName) {
            this.taskName = taskName;
        }

        @Override
        public void update(double fraction, String message) {
            double clampedFraction = Math.clamp(fraction, 0.0, 1.0);
            fractions.compute(taskName, (_, previous) -> Math.max(previous == null ? 0.0 : previous, clampedFraction));
            progressReporter.emit(computeOverallPercent(), message);
        }
    }
}