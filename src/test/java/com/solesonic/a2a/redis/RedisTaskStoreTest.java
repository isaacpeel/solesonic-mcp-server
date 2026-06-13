package com.solesonic.a2a.redis;

import com.google.gson.Gson;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisTaskStoreTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final Gson gson = JsonUtil.OBJECT_MAPPER;

    private RedisTaskStore store;

    @BeforeEach
    void setUp() {
        store = new RedisTaskStore(stringRedisTemplate, gson);
    }

    @Test
    void save_nonFinalTask_setsKeyWithoutExpiry() {
        Task task = buildTask("task-1", TaskState.TASK_STATE_WORKING);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-1")).thenReturn(null);

        store.save(task, false);

        verify(valueOperations).set(eq("a2a:task:task-1"), anyString());
        verify(stringRedisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    void save_finalTask_setsKeyAndAppliesTtl() {
        Task task = buildTask("task-2", TaskState.TASK_STATE_COMPLETED);
        String taskJson = gson.toJson(task);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-2")).thenReturn(taskJson);

        store.save(task, false);

        verify(valueOperations).set(eq("a2a:task:task-2"), anyString());
        verify(stringRedisTemplate).expire("a2a:task:task-2", Duration.ofHours(24));
    }

    @Test
    void get_existingTask_deserializesAndReturns() {
        Task task = buildTask("task-3", TaskState.TASK_STATE_WORKING);
        String taskJson = gson.toJson(task);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-3")).thenReturn(taskJson);

        Task result = store.get("task-3");

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("task-3");
    }

    @Test
    void get_missingTask_returnsNull() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:missing")).thenReturn(null);

        Task result = store.get("missing");

        assertThat(result).isNull();
    }

    @Test
    void delete_delegatesToRedis() {
        store.delete("task-5");

        verify(stringRedisTemplate).delete("a2a:task:task-5");
    }

    @Test
    void isTaskActive_finalizedTask_returnsFalse() {
        Task task = buildTask("task-6", TaskState.TASK_STATE_COMPLETED);
        String taskJson = gson.toJson(task);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-6")).thenReturn(taskJson);

        assertThat(store.isTaskActive("task-6")).isFalse();
    }

    @Test
    void isTaskActive_nonFinalTask_returnsTrue() {
        Task task = buildTask("task-7", TaskState.TASK_STATE_WORKING);
        String taskJson = gson.toJson(task);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-7")).thenReturn(taskJson);

        assertThat(store.isTaskActive("task-7")).isTrue();
    }

    @Test
    void isTaskActive_missingTask_returnsFalse() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:absent")).thenReturn(null);

        assertThat(store.isTaskActive("absent")).isFalse();
    }

    @Test
    void isTaskFinalized_finalizedTask_returnsTrue() {
        Task task = buildTask("task-8", TaskState.TASK_STATE_FAILED);
        String taskJson = gson.toJson(task);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-8")).thenReturn(taskJson);

        assertThat(store.isTaskFinalized("task-8")).isTrue();
    }

    @Test
    void isTaskFinalized_missingTask_returnsFalse() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:not-here")).thenReturn(null);

        assertThat(store.isTaskFinalized("not-here")).isFalse();
    }

    @Test
    void save_replicatedFlag_hasNoEffect() {
        Task task = buildTask("task-9", TaskState.TASK_STATE_WORKING);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-9")).thenReturn(null);

        store.save(task, true);

        verify(valueOperations).set(eq("a2a:task:task-9"), anyString());
        verify(stringRedisTemplate, never()).expire(any(), any(Duration.class));
    }

    private Task buildTask(String taskId, TaskState taskState) {
        return Task.builder()
                .id(taskId)
                .contextId("ctx-" + taskId)
                .status(new TaskStatus(taskState))
                .build();
    }
}
