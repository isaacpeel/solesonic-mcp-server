package com.solesonic.agent.checkpoint;

import com.solesonic.agent.agile.AgileQueryIntent;
import com.solesonic.agent.agile.AgileState;
import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.model.AssigneeCandidate;
import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.agent.model.JiraIssueCreatePayload;
import com.solesonic.agent.nba.SportsState;
import com.solesonic.agent.nba.model.EspnTeamProfile;
import com.solesonic.agent.nba.model.SportsQueryIntent;
import com.solesonic.agent.nba.model.SportsQuestionType;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.agile.Board;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonMapperStateSerializerTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    private final JsonMapperStateSerializer<AgentState> serializer =
            new JsonMapperStateSerializer<>(AgentState::new, JsonMapper.builder().build());

    private Map<String, Object> roundTrip(Map<String, Object> data) throws IOException {
        return serializer.readDataFromString(serializer.writeDataAsString(data));
    }

    @Test
    void agileState_roundTripsDomainTypes() throws IOException {
        Map<String, Object> data = Map.of(
                AgileState.CALLER_IDENTITY, CALLER,
                AgileState.USER_MESSAGE, "move my issues to done",
                AgileState.BOARDS, List.of(new Board(1, "self", "Board", "scrum")),
                AgileState.AGILE_QUERY_INTENT, new AgileQueryIntent(List.of(), "currentUser()", null, null, "", "TRANSITION", 0, "Done"),
                AgileState.ESTIMATED_ITEM_COUNT, 25,
                AgileState.REQUIRES_BATCHING, true);

        Map<String, Object> restored = roundTrip(data);

        assertThat(restored).isEqualTo(data);
        assertThat(restored.get(AgileState.CALLER_IDENTITY)).isInstanceOf(CallerIdentity.class);
        assertThat(new AgileState(restored).boards().orElseThrow().getFirst()).isInstanceOf(Board.class);
    }

    @Test
    void jiraState_roundTripsDomainTypes() throws IOException {
        AssigneeLookupResult assignee = new AssigneeLookupResult(true, "acc-1", "RESOLVED", "Bob");
        Map<String, Object> data = Map.of(
                JiraState.CALLER_IDENTITY, CALLER,
                JiraState.ACCEPTANCE_CRITERIA, List.of("It works"),
                JiraState.ASSIGNEE_CANDIDATES, List.of(new AssigneeCandidate("acc-1", "Bob"), new AssigneeCandidate("acc-2", "Alice")),
                JiraState.ASSIGNEE_LOOKUP_RESULT, assignee,
                JiraState.FINAL_PAYLOAD, new JiraIssueCreatePayload("Login", "Build it", List.of("It works"), assignee));

        Map<String, Object> restored = roundTrip(data);

        assertThat(restored).isEqualTo(data);
        assertThat(new JiraState(restored).assigneeCandidates().orElseThrow().getFirst()).isInstanceOf(AssigneeCandidate.class);
    }

    @Test
    void sportsState_roundTripsDomainTypes() throws IOException {
        Map<String, Object> data = Map.of(
                SportsState.CALLER_IDENTITY, CALLER,
                SportsState.SPORTS_QUERY_INTENT, new SportsQueryIntent(List.of(SportsQuestionType.STANDINGS), List.of("Celtics"), List.of(), "tonight"),
                SportsState.RESOLVED_TEAMS, List.of(new EspnTeamProfile("Boston Celtics", "bos", "boston-celtics", "s", "r", "t")),
                SportsState.SUB_GRAPH_RESULTS, Map.of("standings", "East leaders"));

        Map<String, Object> restored = roundTrip(data);

        assertThat(restored).isEqualTo(data);
        assertThat(new SportsState(restored).resolvedTeams().orElseThrow().getFirst()).isInstanceOf(EspnTeamProfile.class);
    }

    @Test
    void typesOutsideTheAllowList_areRejectedOnRead() throws IOException {
        String written = serializer.writeDataAsString(Map.of("unexpected", URI.create("https://example.test")));

        assertThatThrownBy(() -> serializer.readDataFromString(written)).isInstanceOf(IOException.class);
    }
}
