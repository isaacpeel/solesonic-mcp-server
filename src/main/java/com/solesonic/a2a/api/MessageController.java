package com.solesonic.a2a.api;

import com.solesonic.a2a.service.StreamingA2AService;
import com.solesonic.a2a.service.TaskService;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.jsonrpc.common.json.IdJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageRequest;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

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
                                                           @RequestBody String body) {
        log.info("Sending json agent message: {}", agentName);

        SendMessageRequest request;
        try {
            request = (SendMessageRequest) JSONRPCUtils.parseRequestBody(body, null);
        } catch (IdJsonMappingException idJsonMappingException) {
            return ResponseEntity.ok(new SendMessageResponse(idJsonMappingException.getId(), new InvalidParamsError(idJsonMappingException.getMessage())));
        } catch (JsonProcessingException jsonProcessingException) {
            return ResponseEntity.ok(new SendMessageResponse(null, new InvalidRequestError(jsonProcessingException.getMessage())));
        }

        return taskService.send(agentName, request);
    }

    @PostMapping(path = "/{agentName}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@PathVariable String agentName,
                                    @RequestBody String body) {
        log.info("Sending stream agent message: {}", agentName);

        SendStreamingMessageRequest request;
        try {
            request = (SendStreamingMessageRequest) JSONRPCUtils.parseRequestBody(body, null);
        } catch (IdJsonMappingException idJsonMappingException) {
            return streamingA2AService.sseError(idJsonMappingException.getId(), new InvalidParamsError(idJsonMappingException.getMessage()));
        } catch (JsonProcessingException jsonProcessingException) {
            return streamingA2AService.sseError(null, new InvalidRequestError(jsonProcessingException.getMessage()));
        }

        log.info("Sending streaming request with id: {}", request.getId());
        return streamingA2AService.stream(agentName, request);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException asyncRequestTimeoutException) {
        log.error("Async timeout exception", asyncRequestTimeoutException);

        return ResponseEntity.noContent().build();
    }

    // Re-throws ResponseStatusException so Spring's own resolver keeps its status (e.g. 404 for an unknown agent) instead of flattening it to a 500.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Void> handleUnexpectedError(RuntimeException runtimeException) {
        if (runtimeException instanceof ResponseStatusException responseStatusException) {
            throw responseStatusException;
        }

        log.error("Unexpected error handling A2A request", runtimeException);
        return ResponseEntity.internalServerError().build();
    }
}
