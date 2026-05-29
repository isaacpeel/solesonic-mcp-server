package com.solesonic.agent.jira;

import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.agent.model.JiraIssueCreatePayload;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JiraState extends AgentState {

    public static final String USER_MESSAGE           = "userMessage";
    public static final String CONVERSATION_ID        = "conversationId";
    public static final String STORY_SUMMARY          = "storySummary";
    public static final String DETAILED_DESCRIPTION   = "detailedDescription";
    public static final String ACCEPTANCE_CRITERIA    = "acceptanceCriteria";
    public static final String ASSIGNEE_LOOKUP_RESULT = "assigneeLookupResult";
    public static final String ASSIGNEE_NOT_RESOLVED  = "assigneeNotResolved";
    public static final String FINAL_PAYLOAD          = "finalPayload";

    public JiraState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> userMessage() {
        return value(USER_MESSAGE);
    }

    public Optional<String> conversationId() {
        return value(CONVERSATION_ID);
    }

    public Optional<String> storySummary() {
        return value(STORY_SUMMARY);
    }

    public Optional<String> detailedDescription() {
        return value(DETAILED_DESCRIPTION);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<String>> acceptanceCriteria() {
        return value(ACCEPTANCE_CRITERIA);
    }

    public Optional<AssigneeLookupResult> assigneeLookupResult() {
        return value(ASSIGNEE_LOOKUP_RESULT);
    }

    public Optional<Boolean> assigneeNotResolved() {
        return value(ASSIGNEE_NOT_RESOLVED);
    }

    public Optional<JiraIssueCreatePayload> finalPayload() {
        return value(FINAL_PAYLOAD);
    }
}
