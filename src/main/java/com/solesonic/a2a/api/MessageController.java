package com.solesonic.a2a.api;

import com.solesonic.a2a.service.StreamingA2AService;
import com.solesonic.a2a.service.TaskService;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SendStreamingMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/a2a")
@PreAuthorize("hasAuthority('ROLE_AGENT-EXECUTION')")
public class MessageController {
    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private final TaskService taskService;
    private final StreamingA2AService streamingA2AService;

    public MessageController(TaskService taskService, StreamingA2AService streamingA2AService) {
        this.taskService = taskService;
        this.streamingA2AService = streamingA2AService;
    }

    @PostMapping(path = "/{agentName}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SendMessageResponse> sendMessage(@PathVariable String agentName,
                                                           @RequestBody SendMessageRequest request) {
        log.info("Sending json agent message: {}", agentName);

        return taskService.send(agentName, request);
    }

    @PostMapping(path = "/{agentName}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@PathVariable String agentName,
                                    @RequestBody SendStreamingMessageRequest request) {
        log.info("Sending stream agent message: {}", agentName);

        return streamingA2AService.stream(agentName, request);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException exception) {
        return ResponseEntity.noContent().build();
    }
}
