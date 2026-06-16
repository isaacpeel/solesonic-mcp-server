package com.solesonic.agent.sports.node;

import com.solesonic.agent.sports.EspnTeamRegistry;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.model.EspnTeamProfile;
import com.solesonic.agent.sports.model.SportsQueryIntent;
import com.solesonic.agent.sports.model.SportsQuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolveEspnTeamUrlsNodeTest {

    @Mock
    private EspnTeamRegistry espnTeamRegistry;

    private ResolveEspnTeamUrlsNode node;

    @BeforeEach
    void setUp() {
        node = new ResolveEspnTeamUrlsNode(espnTeamRegistry);
    }

    @Test
    void apply_noTeamsInIntent_returnsEmptyList() throws Exception {
        SportsQueryIntent intent = new SportsQueryIntent(
                List.of(SportsQuestionType.GENERAL_NEWS), List.of(), List.of(), "today");
        SportsState state = new SportsState(Map.of(SportsState.SPORTS_QUERY_INTENT, intent));

        Map<String, Object> result = node.apply(state).get();

        assertThat(result.get(SportsState.RESOLVED_TEAMS)).isEqualTo(List.of());
    }

    @Test
    void apply_allTeamsResolved_returnsAllProfiles() throws Exception {
        EspnTeamProfile lakersProfile = EspnTeamProfile.of("Los Angeles Lakers", "lal", "los-angeles-lakers");
        EspnTeamProfile celticsProfile = EspnTeamProfile.of("Boston Celtics", "bos", "boston-celtics");
        when(espnTeamRegistry.findByName("Los Angeles Lakers")).thenReturn(Optional.of(lakersProfile));
        when(espnTeamRegistry.findByName("Boston Celtics")).thenReturn(Optional.of(celticsProfile));

        SportsQueryIntent intent = new SportsQueryIntent(
                List.of(SportsQuestionType.GAME_PREVIEW),
                List.of("Los Angeles Lakers", "Boston Celtics"),
                List.of(), "today");
        SportsState state = new SportsState(Map.of(SportsState.SPORTS_QUERY_INTENT, intent));

        Map<String, Object> result = node.apply(state).get();

        assertThat(result.get(SportsState.RESOLVED_TEAMS))
                .isEqualTo(List.of(lakersProfile, celticsProfile));
    }

    @Test
    void apply_someTeamsUnresolved_returnsOnlyResolved() throws Exception {
        EspnTeamProfile lakersProfile = EspnTeamProfile.of("Los Angeles Lakers", "lal", "los-angeles-lakers");
        when(espnTeamRegistry.findByName("Los Angeles Lakers")).thenReturn(Optional.of(lakersProfile));
        when(espnTeamRegistry.findByName("Unknown Team")).thenReturn(Optional.empty());

        SportsQueryIntent intent = new SportsQueryIntent(
                List.of(SportsQuestionType.GENERAL_NEWS),
                List.of("Los Angeles Lakers", "Unknown Team"),
                List.of(), "today");
        SportsState state = new SportsState(Map.of(SportsState.SPORTS_QUERY_INTENT, intent));

        Map<String, Object> result = node.apply(state).get();

        assertThat(result.get(SportsState.RESOLVED_TEAMS))
                .isEqualTo(List.of(lakersProfile));
    }

    @Test
    void apply_missingIntent_returnsFailedFuture() {
        SportsState state = new SportsState(Map.of());

        assertThatThrownBy(() -> node.apply(state).get())
                .isInstanceOf(ExecutionException.class);
    }
}
