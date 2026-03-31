package com.solesonic.mcp.workflow;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Component
public class ParallelOrchestrator {
    public record AgentTask<T>(String name, Callable<T> action) {
    }

    public <T> Map<String, T> executeParallel(List<AgentTask<T>> tasks) {
        return tasks.parallelStream()
                .collect(Collectors.toMap(
                        AgentTask::name,
                        task -> {
                            try {
                                return task.action().call();
                            } catch (Exception e) {
                                throw new RuntimeException("Task " + task.name() + " failed", e);
                            }
                        }
                ));
    }
}
