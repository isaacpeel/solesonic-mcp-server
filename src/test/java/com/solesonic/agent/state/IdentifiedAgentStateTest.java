package com.solesonic.agent.state;

import com.solesonic.agent.agile.AgileState;
import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.nba.SportsState;
import com.solesonic.mcp.security.identity.CallerIdentity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifiedAgentStateTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    @Test
    void everyGraphStateCarriesTheCallerUnderTheSameKey() {
        Map<String, Object> input = Map.of(IdentifiedAgentState.CALLER_IDENTITY, CALLER);

        assertThat(new AgileState(input).requireCallerIdentity()).isEqualTo(CALLER);
        assertThat(new JiraState(input).requireCallerIdentity()).isEqualTo(CALLER);
        assertThat(new SportsState(input).requireCallerIdentity()).isEqualTo(CALLER);
    }

    @Test
    void requireCallerIdentity_withoutOne_explainsTheEntryPointMustSupplyIt() {
        assertThatThrownBy(() -> new AgileState(Map.of()).requireCallerIdentity())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(IdentifiedAgentState.CALLER_IDENTITY);
    }
}
