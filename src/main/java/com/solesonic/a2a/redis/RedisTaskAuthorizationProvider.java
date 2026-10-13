package com.solesonic.a2a.redis;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.a2aproject.sdk.server.auth.User;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Per-user task ownership for the A2A server, stored in Redis alongside the tasks themselves.
 * <p>
 * The caller who creates a task owns it, keyed by the JWT subject ({@link User#getUsername()}), and
 * only the owner can read, resume, cancel or subscribe to it. A task with no recorded owner —
 * including any created before ownership was tracked — is denied, so access fails closed.
 * <p>
 * Ownership keys share their task's lifetime: {@link RedisTaskStore} expires and deletes them
 * together with the task.
 */
@NullMarked
public class RedisTaskAuthorizationProvider implements TaskAuthorizationProvider {

    private static final String KEY_PREFIX = "a2a:task-owner:";

    /**
     * The task id the SDK passes with {@link TaskOperation#LIST_TASKS} to ask whether the caller may
     * list tasks at all; the per-task filtering happens afterwards in {@link RedisTaskStore#list}.
     */
    private static final String LIST_SCOPE_TASK_ID = "";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisTaskAuthorizationProvider(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean checkRead(ServerCallContext context, String taskId, TaskOperation operation) {
        if (operation == TaskOperation.LIST_TASKS && LIST_SCOPE_TASK_ID.equals(taskId)) {
            return authenticatedUsername(context) != null;
        }

        return isOwner(context, taskId);
    }

    @Override
    public boolean checkWrite(ServerCallContext context, String taskId, TaskOperation operation) {
        return isOwner(context, taskId);
    }

    @Override
    public boolean checkCreate(ServerCallContext context, TaskOperation operation) {
        return authenticatedUsername(context) != null;
    }

    @Override
    public boolean isTaskRecorded(String taskId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(taskId)));
    }

    /**
     * {@code SETNX}, so two concurrent requests racing to create the same task both succeed and the
     * first writer stays the owner.
     */
    @Override
    public void recordOwnership(ServerCallContext context, String taskId, TaskOperation operation) {
        String username = authenticatedUsername(context);

        if (username == null) {
            return;
        }

        stringRedisTemplate.opsForValue().setIfAbsent(key(taskId), username);
    }

    public void expireOwnership(String taskId, Duration timeToLive) {
        stringRedisTemplate.expire(key(taskId), timeToLive);
    }

    public void deleteOwnership(String taskId) {
        stringRedisTemplate.delete(key(taskId));
    }

    private boolean isOwner(ServerCallContext context, String taskId) {
        String username = authenticatedUsername(context);

        if (username == null) {
            return false;
        }

        String owner = stringRedisTemplate.opsForValue().get(key(taskId));

        return username.equals(owner);
    }

    private static @Nullable String authenticatedUsername(ServerCallContext context) {
        User user = context.getUser();

        if (!user.isAuthenticated()) {
            return null;
        }

        return user.getUsername();
    }

    private static String key(String taskId) {
        return KEY_PREFIX + taskId;
    }
}
