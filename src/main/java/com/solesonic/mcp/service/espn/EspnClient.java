package com.solesonic.mcp.service.espn;

import com.solesonic.mcp.model.espn.EspnRosterResponse;
import com.solesonic.mcp.model.espn.EspnScheduleResponse;
import com.solesonic.mcp.model.espn.EspnStandingsResponse;
import com.solesonic.mcp.model.espn.EspnStatsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import static com.solesonic.mcp.config.espn.EspnConstants.ESPN_API_WEB_CLIENT;
import static com.solesonic.mcp.config.espn.EspnConstants.SCOREBOARD_ENDPOINT;
import static com.solesonic.mcp.config.espn.EspnConstants.STANDINGS_ENDPOINT;
import static com.solesonic.mcp.config.espn.EspnConstants.TEAM_ROSTER_ENDPOINT;
import static com.solesonic.mcp.config.espn.EspnConstants.TEAM_SCHEDULE_ENDPOINT;
import static com.solesonic.mcp.config.espn.EspnConstants.TEAM_STATS_ENDPOINT;

@Component
public class EspnClient {

    private static final Logger log = LoggerFactory.getLogger(EspnClient.class);

    private final WebClient webClient;

    public EspnClient(@Qualifier(ESPN_API_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    public EspnScheduleResponse fetchScoreboard() {
        log.info("Fetching ESPN NBA scoreboard");

        try {
            EspnScheduleResponse response = webClient.get()
                    .uri(SCOREBOARD_ENDPOINT)
                    .retrieve()
                    .bodyToMono(EspnScheduleResponse.class)
                    .block();

            log.info("ESPN scoreboard fetched. Events: {}", response != null && response.events() != null ? response.events().size() : 0);
            return response;
        } catch (Exception exception) {
            log.warn("ESPN scoreboard fetch failed: {}", exception.getMessage());
            return null;
        }
    }

    public EspnScheduleResponse fetchTeamSchedule(String teamAbbreviation) {
        log.info("Fetching ESPN schedule for team: {}", teamAbbreviation);
        try {
            EspnScheduleResponse response = webClient.get()
                    .uri(TEAM_SCHEDULE_ENDPOINT, teamAbbreviation)
                    .retrieve()
                    .bodyToMono(EspnScheduleResponse.class)
                    .block();
            log.info("ESPN team schedule fetched for {}. Events: {}",
                    teamAbbreviation,
                    response != null && response.events() != null ? response.events().size() : 0);
            return response;
        } catch (Exception exception) {
            log.warn("ESPN team schedule fetch failed for {}: {}", teamAbbreviation, exception.getMessage());
            return null;
        }
    }

    public EspnRosterResponse fetchTeamRoster(String teamAbbreviation) {
        log.info("Fetching ESPN roster for team: {}", teamAbbreviation);
        try {
            EspnRosterResponse response = webClient.get()
                    .uri(TEAM_ROSTER_ENDPOINT, teamAbbreviation)
                    .retrieve()
                    .bodyToMono(EspnRosterResponse.class)
                    .block();
            log.info("ESPN roster fetched for {}. Athletes: {}",
                    teamAbbreviation,
                    response != null && response.athletes() != null ? response.athletes().size() : 0);
            return response;
        } catch (Exception exception) {
            log.warn("ESPN roster fetch failed for {}: {}", teamAbbreviation, exception.getMessage());
            return null;
        }
    }

    public EspnStandingsResponse fetchStandings() {
        log.info("Fetching ESPN NBA standings");
        try {
            EspnStandingsResponse response = webClient.get()
                    .uri(STANDINGS_ENDPOINT)
                    .retrieve()
                    .bodyToMono(EspnStandingsResponse.class)
                    .block();
            log.info("ESPN standings fetched. Conferences: {}",
                    response != null && response.children() != null ? response.children().size() : 0);
            return response;
        } catch (Exception exception) {
            log.warn("ESPN standings fetch failed: {}", exception.getMessage());
            return null;
        }
    }

    public EspnStatsResponse fetchTeamStats(String teamAbbreviation) {
        log.info("Fetching ESPN stats for team: {}", teamAbbreviation);
        try {
            EspnStatsResponse response = webClient.get()
                    .uri(TEAM_STATS_ENDPOINT, teamAbbreviation)
                    .retrieve()
                    .bodyToMono(EspnStatsResponse.class)
                    .block();
            log.info("ESPN stats fetched for {}", teamAbbreviation);
            return response;
        } catch (Exception exception) {
            log.warn("ESPN stats fetch failed for {}: {}", teamAbbreviation, exception.getMessage());
            return null;
        }
    }
}
