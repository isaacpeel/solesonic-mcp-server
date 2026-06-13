package com.solesonic.a2a.redis;

import com.google.gson.Gson;
import org.a2aproject.sdk.server.events.EventEnqueueHook;
import org.a2aproject.sdk.server.events.EventQueueItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

public class RedisEventEnqueueHookFactory {

    private static final Logger log = LoggerFactory.getLogger(RedisEventEnqueueHookFactory.class);
    private static final String STREAM_KEY_PREFIX = "a2a:stream:";

    private final StringRedisTemplate stringRedisTemplate;
    private final Gson gson;

    public RedisEventEnqueueHookFactory(StringRedisTemplate stringRedisTemplate, Gson gson) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.gson = gson;
    }

    public EventEnqueueHook create(String taskId) {
        String streamKey = STREAM_KEY_PREFIX + taskId;
        return (EventQueueItem item) -> {
            if (item.isReplicated()) {
                return;
            }
            try {
                var event = item.getEvent();
                String kind = event.getClass().getSimpleName();
                String payload = gson.toJson(event);
                Map<String, String> fields = Map.of("kind", kind, "payload", payload);
                stringRedisTemplate.<String, String>opsForStream()
                        .add(MapRecord.create(streamKey, fields));
            } catch (Exception exception) {
                log.error("Failed to publish event to Redis stream: taskId={}", taskId, exception);
            }
        };
    }
}
