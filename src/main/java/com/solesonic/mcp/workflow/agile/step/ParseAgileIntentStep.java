package com.solesonic.mcp.workflow.agile.step;

import com.solesonic.mcp.workflow.agile.AgileQueryResult;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowContext;
import com.solesonic.mcp.workflow.agile.AgileWorkflowStage;
import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ParseAgileIntentStep implements WorkflowStep<AgileQueryWorkflowContext> {
    public static final String STEP_NAME = "parse-agile-intent";

    private static final Logger log = LoggerFactory.getLogger(ParseAgileIntentStep.class);

    // Prompt is built as a plain String to avoid Spring AI's ST4 template renderer,
    // which treats { and } as delimiters and cannot safely contain JSON literals.
    private static final String PROMPT_TEMPLATE = """
            You are a Jira Query Language (JQL) expert.

            Analyze ONLY what the user explicitly asks for and return a single JSON object with
            exactly three fields:
              jqlFilter  - a JQL expression covering ONLY the conditions the user mentioned
                           (use an empty string if the user wants all issues with no filter)
              queryType  - exactly "COUNT" for quantitative questions ("how many", "count",
                           "number of"), or "LIST" for everything else
              startAt    - the 0-based index to start from. Extract ONLY when the user explicitly
                           requests a page or offset (e.g. "next page" = 15, "page 2" = 15,
                           "third page" = 30, "show me from issue 30" = 29). Use 0 if not mentioned.
                           Assume a page size of 15 for page-based requests.

            IMPORTANT: Only include JQL conditions for things the user explicitly mentions.
            Do NOT add conditions for assignee, issue type, priority, or anything else
            unless the user specifically asks about them.

            JQL translation reference (use ONLY when the user mentions the term):
            - "incomplete" / "not done" / "unfinished" / "to do"  ->  status != Done
            - "to do" specifically                                 ->  status = "To Do"
            - "in progress"                                        ->  status = "In Progress"
            - "open" / "unresolved"                                ->  resolution = Unresolved
            - "assigned to me"                                     ->  assignee = currentUser()
            - "bugs"                                               ->  issuetype = Bug
            - "stories"                                            ->  issuetype = Story
            - "high priority"                                      ->  priority in (High, Highest)

            User request: %s

            Return ONLY the JSON object with no explanation or markdown.
            """;

    private final ChatClient chatClient;
    private final JsonMapper jsonMapper;

    public ParseAgileIntentStep(ChatClient chatClient, JsonMapper jsonMapper) {
        this.chatClient = chatClient;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public boolean isParallelSafe() {
        return true;
    }

    private String stripMarkdownCodeFences(String response) {
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        return response.strip();
    }

    @Override
    public Mono<WorkflowDecision> execute(AgileQueryWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(AgileWorkflowStage.PARSING_INTENT);
        executionContext.progressTracker().step(name()).update(0.1, "Parsing your request");

        String promptText = PROMPT_TEMPLATE.formatted(context.getOriginalUserMessage());

        return Mono.fromCallable(() -> chatClient.prompt().user(promptText).call().content())
                .subscribeOn(Schedulers.boundedElastic())
                .map(responseContent -> {
                    log.debug("Intent parse LLM response: {}", responseContent);

                    try {
                        String jsonContent = stripMarkdownCodeFences(responseContent);
                        AgileQueryResult agileQueryResult = jsonMapper.readValue(jsonContent, AgileQueryResult.class);
                        log.info("Parsed agile intent: queryType={}, jqlFilter={}", agileQueryResult.queryType(), agileQueryResult.jqlFilter());
                        context.setAgileQueryResult(agileQueryResult);
                        executionContext.progressTracker().step(name()).done("Intent parsed");
                        return WorkflowDecision.continueWorkflow();
                    } catch (Exception exception) {
                        log.error("Failed to parse agile intent from LLM response: {}", responseContent, exception);
                        return WorkflowDecision.failed("Could not parse intent from user request: " + exception.getMessage());
                    }
                });
    }
}
