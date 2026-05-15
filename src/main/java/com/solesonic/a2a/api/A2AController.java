package com.solesonic.a2a.api;

import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.DeleteTaskPushNotificationConfigRequest;
import io.a2a.spec.GetTaskPushNotificationConfigRequest;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.ListTaskPushNotificationConfigRequest;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.SetTaskPushNotificationConfigRequest;
import io.a2a.spec.TaskResubscriptionRequest;
import com.solesonic.a2a.service.A2AService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/a2a")
@PreAuthorize("hasAuthority('ROLE_AGENT-EXECUTION')")
public class A2AController {

    private final A2AService a2aService;

    public A2AController(A2AService a2aService) {
        this.a2aService = a2aService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> sendMessage(@RequestBody SendMessageRequest request) {
        return a2aService.send(request);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody SendStreamingMessageRequest request) {
        return a2aService.stream(request);
    }

    @PostMapping(path = "/tasks/get", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getTask(@RequestBody GetTaskRequest request) {
        return a2aService.getTask(request);
    }

    @PostMapping(path = "/tasks/cancel", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> cancelTask(@RequestBody CancelTaskRequest request) {
        return a2aService.cancelTask(request);
    }

    @PostMapping(path = "/tasks/resubscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resubscribeTask(@RequestBody TaskResubscriptionRequest request) {
        return a2aService.resubscribe(request);
    }

    @PostMapping(path = "/tasks/push-notification-configs/set", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> setPushConfig(@RequestBody SetTaskPushNotificationConfigRequest request) {
        return a2aService.setPushConfig(request);
    }

    @PostMapping(path = "/tasks/push-notification-configs/get", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getPushConfig(@RequestBody GetTaskPushNotificationConfigRequest request) {
        return a2aService.getPushConfig(request);
    }

    @PostMapping(path = "/tasks/push-notification-configs/list", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> listPushConfigs(@RequestBody ListTaskPushNotificationConfigRequest request) {
        return a2aService.listPushConfigs(request);
    }

    @PostMapping(path = "/tasks/push-notification-configs/delete", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> deletePushConfig(@RequestBody DeleteTaskPushNotificationConfigRequest request) {
        return a2aService.deletePushConfig(request);
    }
}
