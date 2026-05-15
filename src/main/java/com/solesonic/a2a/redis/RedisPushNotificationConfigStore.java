package com.solesonic.a2a.redis;

import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.spec.PushNotificationConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Component
public class RedisPushNotificationConfigStore implements PushNotificationConfigStore {

    private static final String KEY_PREFIX = "a2a:push-notification-config:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;

    public RedisPushNotificationConfigStore(StringRedisTemplate stringRedisTemplate, JsonMapper jsonMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public PushNotificationConfig setInfo(String taskId, PushNotificationConfig notificationConfig) {
        PushNotificationConfig.Builder builder = new PushNotificationConfig.Builder(notificationConfig);
        if (notificationConfig.id() == null || notificationConfig.id().isEmpty()) {
            builder.id(taskId);
        }
        notificationConfig = builder.build();

        String json = jsonMapper.writeValueAsString(notificationConfig);
        stringRedisTemplate.<String, String>opsForHash().put(KEY_PREFIX + taskId, notificationConfig.id(), json);
        return notificationConfig;
    }

    @Override
    public List<PushNotificationConfig> getInfo(String taskId) {
        Map<String, String> entries = stringRedisTemplate.<String, String>opsForHash().entries(KEY_PREFIX + taskId);
        if (entries.isEmpty()) {
            return null;
        }
        return entries.values().stream()
                .map(json -> jsonMapper.readValue(json, PushNotificationConfig.class))
                .toList();
    }

    @Override
    public void deleteInfo(String taskId, String configId) {
        if (configId == null) {
            configId = taskId;
        }
        stringRedisTemplate.<String, String>opsForHash().delete(KEY_PREFIX + taskId, configId);
    }
}
