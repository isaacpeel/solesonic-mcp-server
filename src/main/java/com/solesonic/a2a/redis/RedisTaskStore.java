package com.solesonic.a2a.redis;

import io.a2a.server.tasks.TaskStateProvider;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.Task;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

public class RedisTaskStore implements TaskStore, TaskStateProvider {

    private static final String KEY_PREFIX = "a2a:task:";
    private static final Duration FINALIZED_TASK_TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;

    public RedisTaskStore(StringRedisTemplate stringRedisTemplate, JsonMapper jsonMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void save(Task task) {
        String key = KEY_PREFIX + task.getId();
        String json = jsonMapper.writeValueAsString(task);
        stringRedisTemplate.opsForValue().set(key, json);
        if (isFinalized(task)) {
            stringRedisTemplate.expire(key, FINALIZED_TASK_TTL);
        }
    }

    @Override
    public Task get(String taskId) {
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + taskId);
        if (json == null) {
            return null;
        }
        return jsonMapper.readValue(json, Task.class);
    }

    @Override
    public void delete(String taskId) {
        stringRedisTemplate.delete(KEY_PREFIX + taskId);
    }

    @Override
    public boolean isTaskActive(String taskId) {
        Task task = get(taskId);
        if (task == null) {
            return false;
        }
        return !isFinalized(task);
    }

    @Override
    public boolean isTaskFinalized(String taskId) {
        Task task = get(taskId);
        if (task == null) {
            return false;
        }
        return isFinalized(task);
    }

    private static boolean isFinalized(Task task) {
        return task.getStatus() != null
                && task.getStatus().state() != null
                && task.getStatus().state().isFinal();
    }
}
