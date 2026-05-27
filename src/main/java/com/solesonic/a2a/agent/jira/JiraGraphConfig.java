package com.solesonic.a2a.agent.jira;

import com.solesonic.a2a.agent.jira.step.AssembleJiraPayloadNode;
import com.solesonic.a2a.agent.jira.step.GenerateUserStoryNode;
import com.solesonic.a2a.agent.jira.step.ResolveAssigneeNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Configuration
public class JiraGraphConfig {

    public static final String GENERATE_USER_STORY = "generateUserStory";
    public static final String RESOLVE_ASSIGNEE    = "resolveAssignee";
    public static final String ASSEMBLE_PAYLOAD    = "assemblePayload";

    @Bean
    public CompiledGraph<JiraState> createJiraGraph(
            GenerateUserStoryNode generateUserStoryNode,
            ResolveAssigneeNode resolveAssigneeNode,
            AssembleJiraPayloadNode assembleJiraPayloadNode
    ) throws GraphStateException {

        return new StateGraph<>(JiraState::new)
                .addNode(GENERATE_USER_STORY, generateUserStoryNode)
                .addNode(RESOLVE_ASSIGNEE, resolveAssigneeNode)
                .addNode(ASSEMBLE_PAYLOAD, assembleJiraPayloadNode)

                .addEdge(START, GENERATE_USER_STORY)
                .addEdge(GENERATE_USER_STORY, RESOLVE_ASSIGNEE)
                .addEdge(RESOLVE_ASSIGNEE, ASSEMBLE_PAYLOAD)
                .addEdge(ASSEMBLE_PAYLOAD, END)

                .compile();
    }
}
