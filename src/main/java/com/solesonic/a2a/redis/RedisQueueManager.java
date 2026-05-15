package com.solesonic.a2a.redis;

import io.a2a.server.events.EventQueue;
import io.a2a.server.events.EventQueueFactory;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.tasks.TaskStateProvider;

public class RedisQueueManager extends InMemoryQueueManager {

    public static RedisQueueManager create(TaskStateProvider taskStateProvider,
                                           RedisEventEnqueueHookFactory hookFactory) {
        HookFactory factory = new HookFactory(taskStateProvider, hookFactory);
        RedisQueueManager manager = new RedisQueueManager(factory, taskStateProvider);
        factory.setQueueManager(manager);
        return manager;
    }

    private RedisQueueManager(HookFactory factory, TaskStateProvider taskStateProvider) {
        super(factory, taskStateProvider);
    }

    private static class HookFactory implements EventQueueFactory {

        private final TaskStateProvider taskStateProvider;
        private final RedisEventEnqueueHookFactory hookFactory;
        private volatile InMemoryQueueManager queueManager;

        HookFactory(TaskStateProvider taskStateProvider, RedisEventEnqueueHookFactory hookFactory) {
            this.taskStateProvider = taskStateProvider;
            this.hookFactory = hookFactory;
        }

        void setQueueManager(InMemoryQueueManager queueManager) {
            this.queueManager = queueManager;
        }

        @Override
        public EventQueue.EventQueueBuilder builder(String taskId) {
            EventQueue.EventQueueBuilder builder = new EventQueue.EventQueueBuilder()
                    .taskId(taskId)
                    .hook(hookFactory.create(taskId))
                    .taskStateProvider(taskStateProvider);
            if (queueManager != null) {
                builder.addOnCloseCallback(queueManager.getCleanupCallback(taskId));
            }
            return builder;
        }
    }
}
