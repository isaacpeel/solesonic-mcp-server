package com.solesonic.agent.state;

import com.solesonic.mcp.security.identity.CallerIdentity;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.Optional;

/**
 * Base for every graph state: carries the {@link CallerIdentity} the graph runs on behalf of.
 * <p>
 * Graph nodes run on pooled threads with no security context, so a node that calls a per-user API
 * reads the caller from here — never from {@code SecurityContextHolder}. Entry points put the
 * caller in the graph input under {@link #CALLER_IDENTITY}; being plain state, it also survives
 * checkpointing and resumption on another thread or instance.
 */
public abstract class IdentifiedAgentState extends AgentState {

    public static final String CALLER_IDENTITY = "callerIdentity";

    protected IdentifiedAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<CallerIdentity> callerIdentity() {
        return value(CALLER_IDENTITY);
    }

    public CallerIdentity requireCallerIdentity() {
        return callerIdentity().orElseThrow(() -> new IllegalStateException(
                "Graph state has no " + CALLER_IDENTITY + "; the graph's entry point must put the caller in the graph input"));
    }
}
