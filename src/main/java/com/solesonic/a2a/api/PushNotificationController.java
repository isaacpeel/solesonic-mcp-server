package com.solesonic.a2a.api;

import com.solesonic.a2a.service.PushNotificationService;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/a2a/{agentName}/tasks/push-notification-configs")
@PreAuthorize("hasAuthority('ROLE_AGENT-EXECUTE')")
public class PushNotificationController {

    private final PushNotificationService pushNotificationService;

    public PushNotificationController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping(path = "/set", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateTaskPushNotificationConfigResponse> setPushConfig(@PathVariable String agentName,
                                                                                  @RequestBody CreateTaskPushNotificationConfigRequest request) {
        return pushNotificationService.setPushConfig(agentName, request);
    }

    @PostMapping(path = "/get", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetTaskPushNotificationConfigResponse> getPushConfig(@PathVariable String agentName,
                                                                               @RequestBody GetTaskPushNotificationConfigRequest request) {
        return pushNotificationService.getPushConfig(agentName, request);
    }

    @PostMapping(path = "/list", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ListTaskPushNotificationConfigsResponse> listPushConfigs(@PathVariable String agentName,
                                                                                   @RequestBody ListTaskPushNotificationConfigsRequest request) {
        return pushNotificationService.listPushConfigs(agentName, request);
    }

    @PostMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeleteTaskPushNotificationConfigResponse> deletePushConfig(@PathVariable String agentName,
                                                                                     @RequestBody DeleteTaskPushNotificationConfigRequest request) {
        return pushNotificationService.deletePushConfig(agentName, request);
    }
}
