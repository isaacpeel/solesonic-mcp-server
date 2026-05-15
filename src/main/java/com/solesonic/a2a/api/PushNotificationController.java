package com.solesonic.a2a.api;

import com.solesonic.a2a.service.PushNotificationService;
import io.a2a.spec.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/a2a/tasks/push-notification-configs")
public class PushNotificationController {

    private final PushNotificationService pushNotificationService;

    public PushNotificationController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping(path = "/set", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SetTaskPushNotificationConfigResponse> setPushConfig(@RequestBody SetTaskPushNotificationConfigRequest request) {
        return pushNotificationService.setPushConfig(request);
    }

    @PostMapping(path = "/get", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetTaskPushNotificationConfigResponse> getPushConfig(@RequestBody GetTaskPushNotificationConfigRequest request) {
        return pushNotificationService.getPushConfig(request);
    }

    @PostMapping(path = "/list", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ListTaskPushNotificationConfigResponse> listPushConfigs(@RequestBody ListTaskPushNotificationConfigRequest request) {
        return pushNotificationService.listPushConfigs(request);
    }

    @PostMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeleteTaskPushNotificationConfigResponse> deletePushConfig(@RequestBody DeleteTaskPushNotificationConfigRequest request) {
        return pushNotificationService.deletePushConfig(request);
    }
}
