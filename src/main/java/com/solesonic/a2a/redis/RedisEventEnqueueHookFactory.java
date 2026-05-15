package com.solesonic.a2a.redis;

import io.a2a.server.events.EventEnqueueHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

public class RedisEventEnqueueHookFactory {

    private static final Logger log = LoggerFactory.getLogger(RedisEventEnqueueHookFactory.class);
    private static final String STREAM_KEY_PREFIX = "a2a:stream:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;

    public RedisEventEnqueueHookFactory(StringRedisTemplate stringRedisTemplate, JsonMapper jsonMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jsonMapper = jsonMapper;
    }

    public EventEnqueueHook create(String taskId) {
        String streamKey = STREAM_KEY_PREFIX + taskId;
        return item -> {
            if (item.isReplicated()) {
                return;
            }
            try {
                var event = item.getEvent();
                String kind = event.getClass().getSimpleName();
                String payload = jsonMapper.writeValueAsString(event);
                Map<String, String> fields = Map.of("kind", kind, "payload", payload);
                stringRedisTemplate.<String, String>opsForStream()
                        .add(MapRecord.create(streamKey, fields));
            } catch (Exception exception) {
                log.error("Failed to publish event to Redis stream: taskId={}", taskId, exception);
            }
        };
    }
}
