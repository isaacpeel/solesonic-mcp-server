package com.solesonic.a2a.api;

import com.solesonic.a2a.service.StreamingA2AService;
import com.solesonic.a2a.service.TaskService;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SendStreamingMessageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/a2a")
//@PreAuthorize("hasAuthority('ROLE_AGENT-EXECUTION')")
public class MessageController {

    private final TaskService taskService;
    private final StreamingA2AService streamingA2AService;

    public MessageController(TaskService taskService, StreamingA2AService streamingA2AService) {
        this.taskService = taskService;
        this.streamingA2AService = streamingA2AService;
    }

    @PostMapping(path = "/{agentName}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SendMessageResponse> sendMessage(@PathVariable String agentName,
                                                           @RequestBody SendMessageRequest request) {
        return taskService.send(agentName, request);
    }

    @PostMapping(path = "/{agentName}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@PathVariable String agentName,
                                    @RequestBody SendStreamingMessageRequest request) {
        return streamingA2AService.stream(agentName, request);
    }
}
