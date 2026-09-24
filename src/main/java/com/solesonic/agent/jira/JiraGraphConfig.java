package com.solesonic.agent.jira;

import com.solesonic.agent.jira.node.AssembleJiraPayloadNode;
import com.solesonic.agent.jira.node.ParallelContentAndAssigneeNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Configuration
public class JiraGraphConfig {

    public static final String GENERATE_CONTENT_AND_RESOLVE_ASSIGNEE = "generateContentAndResolveAssignee";
    public static final String ASSEMBLE_PAYLOAD = "assemblePayload";

    @Bean
    public CompiledGraph<JiraState> createJiraGraph(
            ParallelContentAndAssigneeNode parallelContentAndAssigneeNode,
            AssembleJiraPayloadNode assembleJiraPayloadNode
    ) throws GraphStateException {

        return new StateGraph<>(JiraState::new)
                .addNode(GENERATE_CONTENT_AND_RESOLVE_ASSIGNEE, parallelContentAndAssigneeNode)
                .addNode(ASSEMBLE_PAYLOAD, assembleJiraPayloadNode)

                .addEdge(START, GENERATE_CONTENT_AND_RESOLVE_ASSIGNEE)
                .addEdge(GENERATE_CONTENT_AND_RESOLVE_ASSIGNEE, ASSEMBLE_PAYLOAD)
                .addEdge(ASSEMBLE_PAYLOAD, END)

                .compile();
    }
}
