package com.solesonic.mcp.workflow;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class ParallelOrchestrator {
    public record AgentTask<T>(String name, Supplier<Mono<T>> action) {
    }

    @SuppressWarnings("unchecked")
    public <T> Mono<Map<String, T>> executeParallel(List<AgentTask<?>> tasks) {
        List<Mono<Map.Entry<String, T>>> taskMonos = tasks.stream()
                .map(task -> ((Supplier<Mono<T>>) (Supplier<?>) task.action()).get()
                        .map(result -> Map.entry(task.name(), result))
                        .subscribeOn(Schedulers.boundedElastic()))
                .toList();

        return Mono.zip(taskMonos, results ->
                Arrays.stream(results)
                        .map(r -> (Map.Entry<String, T>) r)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }
}
