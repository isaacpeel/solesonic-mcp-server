package com.solesonic.a2a.config;

import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.DefaultPushNotificationUrlValidator;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class A2APushNotificationConfig {

    /**
     * Push URLs are client-supplied, so the SDK's default validator stays on: HTTPS only, and no
     * private networks or cloud metadata endpoints (SSRF protection).
     */
    @Bean
    public PushNotificationSender pushNotificationSender(PushNotificationConfigStore configStore) {
        return new BasePushNotificationSender(configStore, new DefaultPushNotificationUrlValidator());
    }
}
