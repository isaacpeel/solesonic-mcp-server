package com.solesonic.a2a.workflow.sports;

import com.solesonic.a2a.workflow.sports.model.EspnTeamProfile;
import com.solesonic.a2a.workflow.sports.model.SportsQueryIntent;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("unused")
public class SportsState extends AgentState {

    public static final String USER_MESSAGE = "userMessage";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String CURRENT_DATE_TIME = "currentDateTime";
    public static final String SPORTS_QUERY_INTENT = "sportsQueryIntent";
    public static final String RESOLVED_TEAMS = "resolvedTeams";
    public static final String FOCUS_PLAYER_NAME = "focusPlayerName";
    public static final String SCHEDULE_SEARCH_SUMMARY = "scheduleSearchSummary";
    public static final String NEWS_SEARCH_SUMMARY = "newsSearchSummary";
    public static final String STATISTICS_SEARCH_SUMMARY = "statisticsSearchSummary";
    public static final String ESPN_STANDINGS_DATA = "espnStandingsData";
    public static final String ESPN_STATS_DATA = "espnStatsData";
    public static final String ESPN_ROSTER_DATA = "espnRosterData";
    public static final String ESPN_SCHEDULE_DATA = "espnScheduleData";
    public static final String ROSTER_VALIDATION_SUMMARY = "rosterValidationSummary";
    public static final String DEEP_PLAYER_ANALYSIS_SUMMARY = "deepPlayerAnalysisSummary";
    public static final String FINAL_ANALYSIS = "finalAnalysis";

    public SportsState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> userMessage() {
        return value(USER_MESSAGE);
    }

    public Optional<String> conversationId() {
        return value(CONVERSATION_ID);
    }

    public Optional<String> currentDateTime() {
        return value(CURRENT_DATE_TIME);
    }

    public Optional<SportsQueryIntent> sportsQueryIntent() {
        return value(SPORTS_QUERY_INTENT);
    }

    public Optional<List<EspnTeamProfile>> resolvedTeams() {
        return value(RESOLVED_TEAMS);
    }

    public Optional<String> focusPlayerName() {
        return value(FOCUS_PLAYER_NAME);
    }

    public Optional<String> scheduleSearchSummary() {
        return value(SCHEDULE_SEARCH_SUMMARY);
    }

    public Optional<String> newsSearchSummary() {
        return value(NEWS_SEARCH_SUMMARY);
    }

    public Optional<String> statisticsSearchSummary() {
        return value(STATISTICS_SEARCH_SUMMARY);
    }

    public Optional<String> espnStandingsData() {
        return value(ESPN_STANDINGS_DATA);
    }

    public Optional<String> espnStatsData() {
        return value(ESPN_STATS_DATA);
    }

    public Optional<String> espnRosterData() {
        return value(ESPN_ROSTER_DATA);
    }

    public Optional<String> espnScheduleData() {
        return value(ESPN_SCHEDULE_DATA);
    }

    public Optional<String> rosterValidationSummary() {
        return value(ROSTER_VALIDATION_SUMMARY);
    }

    public Optional<String> deepPlayerAnalysisSummary() {
        return value(DEEP_PLAYER_ANALYSIS_SUMMARY);
    }

    public Optional<String> finalAnalysis() {
        return value(FINAL_ANALYSIS);
    }
}
