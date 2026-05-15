package com.solesonic.a2a.api;

import com.solesonic.a2a.service.StreamingA2AService;
import com.solesonic.a2a.service.TaskService;
import io.a2a.spec.AgentCard;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SendStreamingMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/a2a/jira")
public class CreateJiraAgentController {

    private static final Logger log = LoggerFactory.getLogger(CreateJiraAgentController.class);

    private final AgentCard agentCard;
    private final TaskService taskService;
    private final StreamingA2AService streamingA2AService;

    public CreateJiraAgentController(@Qualifier("createJiraAgentCard") AgentCard agentCard,
                                     @Qualifier("createJiraTaskService") TaskService taskService,
                                     @Qualifier("createJiraStreamingA2AService") StreamingA2AService streamingA2AService) {
        this.agentCard = agentCard;
        this.taskService = taskService;
        this.streamingA2AService = streamingA2AService;
    }

    @GetMapping(path = "/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard getAgentCard() {
        log.debug("Serving create-jira agent card: {}", agentCard.name());
        return agentCard;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SendMessageResponse> sendMessage(@RequestBody SendMessageRequest request) {
        return taskService.send(request);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody SendStreamingMessageRequest request) {
        return streamingA2AService.stream(request);
    }
}
