package com.solesonic.a2a.config;

import io.a2a.server.tasks.BasePushNotificationSender;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class A2APushNotificationConfig {

    @Bean
    public PushNotificationSender pushNotificationSender(PushNotificationConfigStore configStore) {
        return new BasePushNotificationSender(configStore);
    }
}
