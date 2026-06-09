package com.solesonic.agent.jira;

import com.solesonic.agent.jira.node.AssembleJiraPayloadNode;
import com.solesonic.agent.jira.node.GenerateAcceptanceCriteriaNode;
import com.solesonic.agent.jira.node.GenerateDetailedDescriptionNode;
import com.solesonic.agent.jira.node.GenerateStorySummaryNode;
import com.solesonic.agent.jira.node.ResolveAssigneeNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Configuration
public class JiraGraphConfig {

    public static final String GENERATE_DETAILED_DESCRIPTION = "generateDetailedDescription";
    public static final String GENERATE_STORY_SUMMARY = "generateStorySummary";
    public static final String GENERATE_ACCEPTANCE_CRITERIA = "generateAcceptanceCriteria";
    public static final String RESOLVE_ASSIGNEE = "resolveAssignee";
    public static final String ASSEMBLE_PAYLOAD = "assemblePayload";

    @Bean
    public CompiledGraph<JiraState> createJiraGraph(
            GenerateDetailedDescriptionNode generateDetailedDescriptionNode,
            GenerateStorySummaryNode generateStorySummaryNode,
            GenerateAcceptanceCriteriaNode generateAcceptanceCriteriaNode,
            ResolveAssigneeNode resolveAssigneeNode,
            AssembleJiraPayloadNode assembleJiraPayloadNode
    ) throws GraphStateException {

        return new StateGraph<>(JiraState::new)
                .addNode(GENERATE_DETAILED_DESCRIPTION, generateDetailedDescriptionNode)
                .addNode(GENERATE_STORY_SUMMARY, generateStorySummaryNode)
                .addNode(GENERATE_ACCEPTANCE_CRITERIA, generateAcceptanceCriteriaNode)
                .addNode(RESOLVE_ASSIGNEE, resolveAssigneeNode)
                .addNode(ASSEMBLE_PAYLOAD, assembleJiraPayloadNode)

                .addEdge(START, GENERATE_DETAILED_DESCRIPTION)
                .addEdge(GENERATE_DETAILED_DESCRIPTION, GENERATE_STORY_SUMMARY)
                .addEdge(GENERATE_STORY_SUMMARY, GENERATE_ACCEPTANCE_CRITERIA)
                .addEdge(GENERATE_ACCEPTANCE_CRITERIA, RESOLVE_ASSIGNEE)
                .addEdge(RESOLVE_ASSIGNEE, ASSEMBLE_PAYLOAD)
                .addEdge(ASSEMBLE_PAYLOAD, END)

                .compile();
    }
}
