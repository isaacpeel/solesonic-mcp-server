package com.solesonic.mcp.workflow.framework;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class WorkflowDefinition<C extends WorkflowContext> {
    private final String name;
    private final List<StepGroup<C>> stepGroups;

    private WorkflowDefinition(String name, List<StepGroup<C>> stepGroups) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.stepGroups = List.copyOf(stepGroups);
    }

    public String name() {
        return name;
    }

    public List<StepGroup<C>> stepGroups() {
        return stepGroups;
    }

    public static <C extends WorkflowContext> Builder<C> builder(String name) {
        return new Builder<>(name);
    }

    public record StepGroup<C extends WorkflowContext>(ExecutionMode executionMode, List<WorkflowStep<C>> steps) {
        public StepGroup {
            Objects.requireNonNull(executionMode, "executionMode must not be null");
            Objects.requireNonNull(steps, "steps must not be null");

            if (steps.isEmpty()) {
                throw new IllegalArgumentException("steps must not be empty");
            }

            steps = List.copyOf(steps);
        }
    }

    public enum ExecutionMode {
        SEQUENTIAL,
        PARALLEL
    }

    public static final class Builder<C extends WorkflowContext> {
        private final String name;
        private final List<StepGroup<C>> groups;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
            this.groups = new ArrayList<>();
        }

        @SafeVarargs
        public final Builder<C> sequential(WorkflowStep<C>... steps) {
            List<WorkflowStep<C>> sequentialSteps = Arrays.stream(steps)
                    .peek(step -> Objects.requireNonNull(step, "step must not be null"))
                    .toList();
            groups.add(new StepGroup<>(ExecutionMode.SEQUENTIAL, sequentialSteps));
            return this;
        }

        @SafeVarargs
        public final Builder<C> parallel(WorkflowStep<C>... steps) {
            List<WorkflowStep<C>> parallelSteps = Arrays.stream(steps)
                    .peek(step -> Objects.requireNonNull(step, "step must not be null"))
                    .peek(step -> {
                        if (!step.isParallelSafe()) {
                            throw new IllegalArgumentException(
                                    "Step '" + step.name() + "' is not parallel-safe and cannot be added to a parallel group"
                            );
                        }
                    })
                    .toList();
            groups.add(new StepGroup<>(ExecutionMode.PARALLEL, parallelSteps));
            return this;
        }

        public WorkflowDefinition<C> build() {
            if (groups.isEmpty()) {
                throw new IllegalArgumentException("workflow must have at least one step group");
            }

            return new WorkflowDefinition<>(name, groups);
        }
    }
}
