package com.solesonic.agent.agile;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgileState extends AgentState {

    public static final String USER_MESSAGE         = "userMessage";
    public static final String CONVERSATION_ID      = "conversationId";
    public static final String AGILE_QUERY_RESULT   = "agileQueryResult";
    public static final String BOARDS               = "boards";
    public static final String ESTIMATED_ITEM_COUNT = "estimatedItemCount";
    public static final String REQUIRES_BATCHING    = "requiresBatching";
    public static final String BATCH_SIZE           = "batchSize";

    public AgileState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> userMessage() {
        return value(USER_MESSAGE);
    }

    public Optional<String> conversationId() {
        return value(CONVERSATION_ID);
    }

    public Optional<AgileQueryResult> agileQueryResult() {
        return value(AGILE_QUERY_RESULT);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<?>> boards() {
        return value(BOARDS);
    }

    public Optional<Integer> estimatedItemCount() {
        return value(ESTIMATED_ITEM_COUNT);
    }

    public Optional<Boolean> requiresBatching() {
        return value(REQUIRES_BATCHING);
    }

    public Optional<Integer> batchSize() {
        return value(BATCH_SIZE);
    }
}
