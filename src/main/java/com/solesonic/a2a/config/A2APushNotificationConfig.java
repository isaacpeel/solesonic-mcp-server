package com.solesonic.a2a.config;

import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class A2APushNotificationConfig {

    @Bean
    public PushNotificationSender pushNotificationSender(PushNotificationConfigStore configStore) {
        return new BasePushNotificationSender(configStore);
    }
}
