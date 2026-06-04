package com.solesonic.a2a.redis;

import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

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

    @Mock
    private JsonMapper jsonMapper;

    private RedisTaskStore store;

    @BeforeEach
    void setUp() {
        store = new RedisTaskStore(stringRedisTemplate, jsonMapper);
    }

    @Test
    void save_nonFinalTask_setsKeyWithoutExpiry() {
        Task task = buildTask("task-1", TaskState.WORKING);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jsonMapper.writeValueAsString(task)).thenReturn("{\"id\":\"task-1\"}");

        store.save(task);

        verify(valueOperations).set(eq("a2a:task:task-1"), anyString());
        verify(stringRedisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    void save_finalTask_setsKeyAndAppliesTtl() {
        Task task = buildTask("task-2", TaskState.COMPLETED);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jsonMapper.writeValueAsString(task)).thenReturn("{\"id\":\"task-2\"}");

        store.save(task);

        verify(valueOperations).set(eq("a2a:task:task-2"), anyString());
        verify(stringRedisTemplate).expire("a2a:task:task-2", Duration.ofHours(24));
    }

    @Test
    void get_existingTask_deserializesAndReturns() {
        Task task = buildTask("task-3", TaskState.WORKING);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-3")).thenReturn("{\"id\":\"task-3\"}");
        when(jsonMapper.readValue(anyString(), eq(Task.class))).thenReturn(task);

        Task result = store.get("task-3");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("task-3");
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
        Task task = buildTask("task-6", TaskState.COMPLETED);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-6")).thenReturn("{\"id\":\"task-6\"}");
        when(jsonMapper.readValue(anyString(), eq(Task.class))).thenReturn(task);

        assertThat(store.isTaskActive("task-6")).isFalse();
    }

    @Test
    void isTaskActive_nonFinalTask_returnsTrue() {
        Task task = buildTask("task-7", TaskState.WORKING);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-7")).thenReturn("{\"id\":\"task-7\"}");
        when(jsonMapper.readValue(anyString(), eq(Task.class))).thenReturn(task);

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
        Task task = buildTask("task-8", TaskState.FAILED);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:task-8")).thenReturn("{\"id\":\"task-8\"}");
        when(jsonMapper.readValue(anyString(), eq(Task.class))).thenReturn(task);

        assertThat(store.isTaskFinalized("task-8")).isTrue();
    }

    @Test
    void isTaskFinalized_missingTask_returnsFalse() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("a2a:task:not-here")).thenReturn(null);

        assertThat(store.isTaskFinalized("not-here")).isFalse();
    }

    private Task buildTask(String taskId, TaskState taskState) {
        return new Task.Builder()
                .id(taskId)
                .contextId("ctx-" + taskId)
                .status(new TaskStatus(taskState))
                .build();
    }
}
