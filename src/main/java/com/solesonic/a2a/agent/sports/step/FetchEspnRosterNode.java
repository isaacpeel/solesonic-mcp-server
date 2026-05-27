package com.solesonic.a2a.agent.sports.step;

import com.solesonic.mcp.service.espn.EspnService;
import com.solesonic.a2a.agent.sports.SportsState;
import com.solesonic.a2a.agent.sports.model.EspnTeamProfile;
import com.solesonic.a2a.agent.sports.model.SportsQueryIntent;
import com.solesonic.a2a.agent.sports.model.SportsQuestionType;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class FetchEspnRosterNode implements AsyncNodeAction<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(FetchEspnRosterNode.class);

    private static final Set<SportsQuestionType> ROSTER_RELEVANT_TYPES = Set.of(
            SportsQuestionType.GAME_PREVIEW,
            SportsQuestionType.PLAYER_ANALYSIS,
            SportsQuestionType.TRADE_NEWS
    );

    private final EspnService espnService;

    public FetchEspnRosterNode(EspnService espnService) {
        this.espnService = espnService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state) {
        try {
            List<EspnTeamProfile> resolvedTeams = state.resolvedTeams().orElse(List.of());

            if (resolvedTeams.isEmpty()) {
                log.info("No resolved teams — skipping ESPN roster fetch");
                return completedFuture(Map.of(SportsState.ESPN_ROSTER_DATA, "No teams resolved for roster fetch."));
            }

            SportsQueryIntent intent = state.sportsQueryIntent().orElseThrow();
            boolean isRosterQuestion = intent.questionTypes().stream()
                    .anyMatch(ROSTER_RELEVANT_TYPES::contains);

            if (!isRosterQuestion) {
                log.info("Skipping roster fetch for question type: {}", intent.questionTypes());
                return completedFuture(Map.of(SportsState.ESPN_ROSTER_DATA, "Roster not needed for this query type."));
            }

            List<String> teamAbbreviations = resolvedTeams.stream()
                    .map(EspnTeamProfile::abbreviation)
                    .toList();

            log.info("Fetching ESPN roster via API. Teams: {}", teamAbbreviations);

            try {
                String rosterData = espnService.getRosterData(teamAbbreviations);
                return completedFuture(Map.of(SportsState.ESPN_ROSTER_DATA, rosterData));
            } catch (Exception exception) {
                log.error("Failed to fetch ESPN roster data", exception);
                return completedFuture(Map.of(SportsState.ESPN_ROSTER_DATA, "ESPN roster data unavailable."));
            }
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }
}
