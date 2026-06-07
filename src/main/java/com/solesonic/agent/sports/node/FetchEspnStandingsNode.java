package com.solesonic.agent.sports.node;

import com.solesonic.agent.sports.SportsState;
import com.solesonic.service.espn.EspnService;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class FetchEspnStandingsNode implements AsyncNodeAction<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(FetchEspnStandingsNode.class);

    private final EspnService espnService;

    public FetchEspnStandingsNode(EspnService espnService) {
        this.espnService = espnService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state) {
        try {
            log.info("Fetching ESPN standings");

            String standingsData = espnService.getStandingsData();

            return completedFuture(Map.of(SportsState.STATISTICS_SEARCH_SUMMARY, standingsData));
        } catch (Exception exception) {
            log.error("Failed to fetch ESPN standings", exception);
            return failedFuture(exception);
        }
    }
}
