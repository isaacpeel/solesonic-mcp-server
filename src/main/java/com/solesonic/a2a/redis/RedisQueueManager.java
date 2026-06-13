package com.solesonic.a2a.redis;

import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.server.events.EventQueueFactory;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.jspecify.annotations.NonNull;

public class RedisQueueManager extends InMemoryQueueManager {

    public static RedisQueueManager create(TaskStateProvider taskStateProvider,
                                           RedisEventEnqueueHookFactory hookFactory,
                                           MainEventBus mainEventBus) {
        HookFactory factory = new HookFactory(hookFactory);
        RedisQueueManager manager = new RedisQueueManager(factory, taskStateProvider, mainEventBus);
        factory.setQueueManager(manager);
        return manager;
    }

    private RedisQueueManager(HookFactory factory, TaskStateProvider taskStateProvider, MainEventBus mainEventBus) {
        super(factory, taskStateProvider, mainEventBus);
    }

    private static class HookFactory implements EventQueueFactory {

        private final RedisEventEnqueueHookFactory hookFactory;
        private volatile InMemoryQueueManager queueManager;

        HookFactory(RedisEventEnqueueHookFactory hookFactory) {
            this.hookFactory = hookFactory;
        }

        void setQueueManager(InMemoryQueueManager queueManager) {
            this.queueManager = queueManager;
        }

        @Override
        public EventQueue.@NonNull EventQueueBuilder builder(@NonNull String taskId) {
            return queueManager.createBaseEventQueueBuilder(taskId)
                    .hook(hookFactory.create(taskId));
        }
    }
}
