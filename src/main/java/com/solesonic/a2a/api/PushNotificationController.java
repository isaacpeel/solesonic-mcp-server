package com.solesonic.a2a.api;

import com.solesonic.a2a.service.PushNotificationService;
import io.a2a.spec.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/a2a/{agentName}/tasks/push-notification-configs")
public class PushNotificationController {

    private final PushNotificationService pushNotificationService;

    public PushNotificationController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping(path = "/set", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SetTaskPushNotificationConfigResponse> setPushConfig(@PathVariable String agentName,
                                                                               @RequestBody SetTaskPushNotificationConfigRequest request) {
        return pushNotificationService.setPushConfig(agentName, request);
    }

    @PostMapping(path = "/get", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetTaskPushNotificationConfigResponse> getPushConfig(@PathVariable String agentName,
                                                                               @RequestBody GetTaskPushNotificationConfigRequest request) {
        return pushNotificationService.getPushConfig(agentName, request);
    }

    @PostMapping(path = "/list", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ListTaskPushNotificationConfigResponse> listPushConfigs(@PathVariable String agentName,
                                                                                  @RequestBody ListTaskPushNotificationConfigRequest request) {
        return pushNotificationService.listPushConfigs(agentName, request);
    }

    @PostMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeleteTaskPushNotificationConfigResponse> deletePushConfig(@PathVariable String agentName,
                                                                                     @RequestBody DeleteTaskPushNotificationConfigRequest request) {
        return pushNotificationService.deletePushConfig(agentName, request);
    }
}
