package com.solesonic.agent.sports.node;

import com.solesonic.agent.sports.EspnTeamRegistry;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.model.EspnTeamProfile;
import com.solesonic.mcp.model.espn.EspnCompetition;
import com.solesonic.mcp.model.espn.EspnCompetitor;
import com.solesonic.mcp.model.espn.EspnEvent;
import com.solesonic.mcp.model.espn.EspnScheduleSummary;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class ExtractTeamsFromScheduleNode implements AsyncNodeAction<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(ExtractTeamsFromScheduleNode.class);

    private final EspnTeamRegistry espnTeamRegistry;

    public ExtractTeamsFromScheduleNode(EspnTeamRegistry espnTeamRegistry) {
        this.espnTeamRegistry = espnTeamRegistry;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state) {
        try {
            Optional<EspnScheduleSummary> scheduleOptional = state.espnScheduleSummaryObject();

            if (scheduleOptional.isEmpty()) {
                log.warn("No structured schedule object in state — cannot extract teams; roster fetch will be skipped");
                return completedFuture(Map.of(SportsState.RESOLVED_TEAMS, List.of()));
            }

            EspnScheduleSummary scheduleSummary = scheduleOptional.get();

            Optional<EspnEvent> targetGame = scheduleSummary.liveGames().stream().findFirst()
                    .or(scheduleSummary::nextUpcomingGame);

            if (targetGame.isEmpty()) {
                log.warn("No live or upcoming game found in schedule — cannot extract teams");
                return completedFuture(Map.of(SportsState.RESOLVED_TEAMS, List.of()));
            }

            EspnEvent game = targetGame.get();
            List<EspnCompetitor> competitors = extractCompetitors(game);

            List<EspnTeamProfile> resolvedTeams = competitors.stream()
                    .filter(competitor -> competitor.team() != null && competitor.team().abbreviation() != null)
                    .map(competitor -> espnTeamRegistry.findByName(competitor.team().abbreviation()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .distinct()
                    .toList();

            if (resolvedTeams.isEmpty()) {
                log.warn("Could not resolve any teams from schedule game: {}", game.shortName());
            } else {
                log.info("Extracted teams from schedule: {}",
                        resolvedTeams.stream().map(EspnTeamProfile::fullName).toList());
            }

            return completedFuture(Map.of(SportsState.RESOLVED_TEAMS, resolvedTeams));
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }

    private List<EspnCompetitor> extractCompetitors(EspnEvent game) {
        if (game.competitions() == null || game.competitions().isEmpty()) {
            return List.of();
        }

        EspnCompetition competition = game.competitions().getFirst();

        if (competition.competitors() == null) {
            return List.of();
        }

        return competition.competitors();
    }
}
