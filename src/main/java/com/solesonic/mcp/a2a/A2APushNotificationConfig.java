package com.solesonic.mcp.a2a;

import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.BasePushNotificationSender;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import org.springaicommunity.a2a.server.controller.MessageController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;

@Configuration
public class A2APushNotificationConfig {

    @Bean
    public PushNotificationSender pushNotificationSender(PushNotificationConfigStore configStore) {
        return new BasePushNotificationSender(configStore);
    }

    @Bean
    public MessageController messageController(RequestHandler requestHandler) {
        return new DisabledDefaultMessageController(requestHandler);
    }

    @RequestMapping("/__a2a-disabled-default-message-controller")
    static class DisabledDefaultMessageController extends MessageController {
        DisabledDefaultMessageController(RequestHandler requestHandler) {
            super(requestHandler);
        }
    }
}
