package com.solesonic.a2a.api;

import com.solesonic.a2a.service.StreamingA2AService;
import com.solesonic.a2a.service.TaskService;
import io.a2a.spec.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/a2a/{agentName}/tasks")
//@PreAuthorize("hasAuthority('ROLE_AGENT-EXECUTION')")
public class TaskController {

    private final TaskService taskService;
    private final StreamingA2AService streamingA2AService;

    public TaskController(TaskService taskService, StreamingA2AService streamingA2AService) {
        this.taskService = taskService;
        this.streamingA2AService = streamingA2AService;
    }

    @PostMapping(path = "get", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetTaskResponse> getTask(@PathVariable String agentName,
                                                   @RequestBody GetTaskRequest request) {
        return taskService.getTask(agentName, request);
    }

    @PostMapping(path = "cancel", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CancelTaskResponse> cancelTask(@PathVariable String agentName,
                                                         @RequestBody CancelTaskRequest request) {
        return taskService.cancelTask(agentName, request);
    }

    @PostMapping(path = "resubscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resubscribeTask(@PathVariable String agentName,
                                      @RequestBody TaskResubscriptionRequest request) {
        return streamingA2AService.resubscribe(agentName, request);
    }
}
