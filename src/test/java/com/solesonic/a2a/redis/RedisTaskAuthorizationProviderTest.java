package com.solesonic.a2a.redis;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.a2aproject.sdk.server.auth.User;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisTaskAuthorizationProviderTest {

    private static final String OWNER_KEY = "a2a:task-owner:task-1";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisTaskAuthorizationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RedisTaskAuthorizationProvider(stringRedisTemplate);
    }

    @Test
    void owner_canReadAndWriteTheirTask() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OWNER_KEY)).thenReturn("alice");

        assertThat(provider.checkRead(context("alice"), "task-1", TaskOperation.GET_TASK)).isTrue();
        assertThat(provider.checkWrite(context("alice"), "task-1", TaskOperation.CANCEL_TASK)).isTrue();
    }

    @Test
    void anotherUser_isDeniedSomeoneElsesTask() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OWNER_KEY)).thenReturn("alice");

        assertThat(provider.checkRead(context("bob"), "task-1", TaskOperation.GET_TASK)).isFalse();
        assertThat(provider.checkWrite(context("bob"), "task-1", TaskOperation.MESSAGE_SEND)).isFalse();
    }

    @Test
    void taskWithNoRecordedOwner_isDenied() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OWNER_KEY)).thenReturn(null);

        assertThat(provider.checkRead(context("alice"), "task-1", TaskOperation.SUBSCRIBE_TO_TASK)).isFalse();
    }

    @Test
    void unauthenticatedCaller_isDeniedEverythingWithoutTouchingRedis() {
        ServerCallContext anonymous = new ServerCallContext(new TestUser("alice", false), Map.of(), Set.of());

        assertThat(provider.checkCreate(anonymous, TaskOperation.MESSAGE_SEND)).isFalse();
        assertThat(provider.checkRead(anonymous, "task-1", TaskOperation.GET_TASK)).isFalse();
        assertThat(provider.checkRead(anonymous, "", TaskOperation.LIST_TASKS)).isFalse();

        provider.recordOwnership(anonymous, "task-1", TaskOperation.MESSAGE_SEND);

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void authenticatedCaller_mayCreateAndListWithoutOwningATask() {
        assertThat(provider.checkCreate(context("alice"), TaskOperation.MESSAGE_SEND)).isTrue();
        assertThat(provider.checkRead(context("alice"), "", TaskOperation.LIST_TASKS)).isTrue();

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void recordOwnership_keepsTheFirstWriterAsOwner() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        provider.recordOwnership(context("alice"), "task-1", TaskOperation.MESSAGE_SEND);

        verify(valueOperations).setIfAbsent(OWNER_KEY, "alice");
    }

    @Test
    void isTaskRecorded_reflectsWhetherTheOwnerKeyExists() {
        when(stringRedisTemplate.hasKey(OWNER_KEY)).thenReturn(true);

        assertThat(provider.isTaskRecorded("task-1")).isTrue();
    }

    @Test
    void ownershipLifetime_followsTheTask() {
        provider.expireOwnership("task-1", Duration.ofHours(24));
        provider.deleteOwnership("task-1");

        verify(stringRedisTemplate).expire(OWNER_KEY, Duration.ofHours(24));
        verify(stringRedisTemplate).delete(OWNER_KEY);
    }

    private static ServerCallContext context(String username) {
        return new ServerCallContext(new TestUser(username, true), Map.of(), Set.of());
    }

    private record TestUser(String username, boolean authenticated) implements User {

        @Override
        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        public @NonNull String getUsername() {
            return username;
        }
    }
}
