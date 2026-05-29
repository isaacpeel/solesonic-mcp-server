package com.solesonic.agent.sports.node;

import com.solesonic.agent.sports.EspnTeamRegistry;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.model.EspnTeamProfile;
import com.solesonic.agent.sports.model.SportsQueryIntent;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class ResolveEspnTeamUrlsNode implements AsyncNodeAction<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(ResolveEspnTeamUrlsNode.class);

    private final EspnTeamRegistry espnTeamRegistry;

    public ResolveEspnTeamUrlsNode(EspnTeamRegistry espnTeamRegistry) {
        this.espnTeamRegistry = espnTeamRegistry;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state) {
        try {
            SportsQueryIntent intent = state.sportsQueryIntent().orElseThrow();

            if (!intent.hasTeams()) {
                log.info("No teams in intent — skipping ESPN team URL resolution");
                return completedFuture(Map.of(SportsState.RESOLVED_TEAMS, List.of()));
            }

            List<EspnTeamProfile> resolvedTeams = new ArrayList<>();
            List<String> unresolvedTeams = new ArrayList<>();

            for (String teamName : intent.teams()) {
                espnTeamRegistry.findByName(teamName).ifPresentOrElse(
                        resolvedTeams::add,
                        () -> unresolvedTeams.add(teamName)
                );
            }

            if (!unresolvedTeams.isEmpty()) {
                log.warn("Could not resolve ESPN URLs for teams: {}", unresolvedTeams);
            }

            log.info("Resolved {} of {} teams to ESPN URLs: {}",
                    resolvedTeams.size(), intent.teams().size(),
                    resolvedTeams.stream().map(EspnTeamProfile::fullName).toList());

            return completedFuture(Map.of(SportsState.RESOLVED_TEAMS, resolvedTeams));
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }
}
