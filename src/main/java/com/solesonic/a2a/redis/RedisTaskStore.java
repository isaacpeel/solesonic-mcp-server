package com.solesonic.a2a.redis;

import com.google.gson.Gson;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class RedisTaskStore implements TaskStore, TaskStateProvider {

    private static final String KEY_PREFIX = "a2a:task:";
    private static final Duration FINALIZED_TASK_TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;
    private final Gson gson;

    public RedisTaskStore(StringRedisTemplate stringRedisTemplate, Gson gson) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.gson = gson;
    }

    @Override
    public void save(Task task, boolean isReplicated) {
        String key = KEY_PREFIX + task.id();
        String json = gson.toJson(task);
        stringRedisTemplate.opsForValue().set(key, json);

        if (isTaskFinalized(task.id())) {
            stringRedisTemplate.expire(key, FINALIZED_TASK_TTL);
        }
    }

    @Override
    public Task get(@NonNull String taskId) {
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + taskId);
        if (json == null) {
            return null;
        }
        return gson.fromJson(json, Task.class);
    }

    @Override
    public void delete(@NonNull String taskId) {
        stringRedisTemplate.delete(KEY_PREFIX + taskId);
    }

    @Override
    @NonNull
    public ListTasksResult list(@NonNull ListTasksParams listTasksParams) {
        Set<String> keys = stringRedisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return new ListTasksResult(List.of());
        }

        List<Task> matchingTasks = new ArrayList<>();
        for (String key : keys) {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                continue;
            }
            Task task = gson.fromJson(json, Task.class);
            if (matchesFilters(task, listTasksParams)) {
                matchingTasks.add(task);
            }
        }

        matchingTasks.sort(Comparator.comparing(Task::id));

        int totalSize = matchingTasks.size();
        int effectivePageSize = listTasksParams.getEffectivePageSize();
        int offset = decodePageToken(listTasksParams.pageToken());

        int fromIndex = Math.min(offset, totalSize);
        int toIndex = Math.min(fromIndex + effectivePageSize, totalSize);
        List<Task> page = matchingTasks.subList(fromIndex, toIndex);

        int historyLength = listTasksParams.getEffectiveHistoryLength();
        boolean includeArtifacts = listTasksParams.shouldIncludeArtifacts();
        List<Task> resultTasks = page.stream()
                .map(task -> applyViewOptions(task, historyLength, includeArtifacts))
                .toList();

        String nextPageToken = toIndex < totalSize ? String.valueOf(toIndex) : null;

        return new ListTasksResult(resultTasks, totalSize, resultTasks.size(), nextPageToken);
    }

    @Override
    public boolean isTaskActive(@NonNull String taskId) {
        return !isTaskFinalized(taskId);
    }

    @Override
    public boolean isTaskFinalized(@NonNull String taskId) {
        Task task = get(taskId);
        if (task == null) {
            return false;
        }

        return task.status().state().isFinal();
    }

    private boolean matchesFilters(Task task, ListTasksParams params) {
        if (params.contextId() != null && !params.contextId().equals(task.contextId())) {
            return false;
        }

        if (params.status() != null && params.status() != task.status().state()) {
            return false;
        }

        return params.statusTimestampAfter() == null ||
                task.status().timestamp().toInstant().isAfter(params.statusTimestampAfter());
    }

    private Task applyViewOptions(Task task, int historyLength, boolean includeArtifacts) {
        List<Message> trimmedHistory;
        if (historyLength == 0) {
            trimmedHistory = List.of();
        } else {
            assert task.history() != null;
            trimmedHistory = task.history().subList(Math.max(0, task.history().size() - historyLength), task.history().size());
        }
        List<Artifact> artifacts = includeArtifacts ? task.artifacts() : List.of();
        return Task.builder(task)
                .history(trimmedHistory)
                .artifacts(artifacts)
                .build();
    }

    private int decodePageToken(String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(pageToken);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
