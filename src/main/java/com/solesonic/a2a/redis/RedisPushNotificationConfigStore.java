package com.solesonic.a2a.redis;

import com.google.gson.Gson;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

public class RedisPushNotificationConfigStore implements PushNotificationConfigStore {

    private static final String KEY_PREFIX = "a2a:push-notification-config:";

    private final StringRedisTemplate stringRedisTemplate;
    private final Gson gson;

    public RedisPushNotificationConfigStore(StringRedisTemplate stringRedisTemplate, Gson gson) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.gson = gson;
    }

    @Override
    public TaskPushNotificationConfig setInfo(TaskPushNotificationConfig notificationConfig) {
        String taskId = notificationConfig.taskId();
        TaskPushNotificationConfig.Builder builder = TaskPushNotificationConfig.builder(notificationConfig);
        if (notificationConfig.id() == null || notificationConfig.id().isEmpty()) {
            builder.id(taskId);
        }
        notificationConfig = builder.build();

        String json = gson.toJson(notificationConfig);
        stringRedisTemplate.<String, String>opsForHash().put(KEY_PREFIX + taskId, notificationConfig.id(), json);
        return notificationConfig;
    }

    @Override
    public ListTaskPushNotificationConfigsResult getInfo(ListTaskPushNotificationConfigsParams params) {
        Map<String, String> entries = stringRedisTemplate.<String, String>opsForHash().entries(KEY_PREFIX + params.id());
        if (entries.isEmpty()) {
            return new ListTaskPushNotificationConfigsResult(List.of(), null);
        }
        List<TaskPushNotificationConfig> configs = entries.values().stream()
                .map(json -> gson.fromJson(json, TaskPushNotificationConfig.class))
                .toList();
        return new ListTaskPushNotificationConfigsResult(configs, null);
    }

    @Override
    public void deleteInfo(String taskId, String configId) {
        if (configId == null) {
            configId = taskId;
        }
        stringRedisTemplate.<String, String>opsForHash().delete(KEY_PREFIX + taskId, configId);
    }
}
