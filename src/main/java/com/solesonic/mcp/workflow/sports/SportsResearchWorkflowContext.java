package com.solesonic.mcp.workflow.sports;

import com.solesonic.mcp.workflow.framework.WorkflowContext;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import com.solesonic.mcp.workflow.sports.model.EspnTeamProfile;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public class SportsResearchWorkflowContext implements WorkflowContext {

    public static final String NBA_TERMINOLOGY = """
            NBA TERMINOLOGY REFERENCE:
              Double-double    — A player achieves double digits (10+) in two major statistical categories \
            in a single game (e.g., 20 points and 11 rebounds).
              Triple-double    — A player achieves double digits (10+) in three major statistical categories \
            in a single game (e.g., 25 points, 10 rebounds, and 10 assists).
              Quadruple-double — Extremely rare; double digits in four statistical categories in a single game.
              Assist           — A pass that directly leads to a made basket by a teammate.
              Turnover         — Loss of ball possession without taking a shot (e.g., bad pass, travel).
              Steal            — Taking the ball away from an opposing player, causing a change of possession.
              Block            — Legally deflecting an opponent's shot attempt.
              Rebound          — Recovering the ball after a missed shot. Offensive rebound: your own team missed; \
            defensive rebound: the opponent missed.
              Paint / The Lane — The rectangular painted area under each basket (also called the key or lane).
              Pick and roll    — An offensive play where one player sets a screen (pick) for the ball-handler, \
            then cuts toward the basket (rolls) to receive a pass.
              Iso / Isolation  — A one-on-one offensive play where the ball-handler takes on a defender \
            without screens, relying on individual skill.
              Fast break       — An offensive push where the team advances quickly upcourt before the defense \
            can set, often producing easy baskets.
              Sixth man        — The first player off the bench; often a key scorer or playmaker despite not starting.
              Plus/minus (+/-) — The team's scoring margin (points scored minus points allowed) while \
            a specific player is on the court.
              FG% / 3P% / FT% — Field goal percentage, three-point percentage, and free throw percentage: \
            shooting accuracy metrics by zone or situation.
              True Shooting % (TS%) — A comprehensive shooting efficiency metric accounting for field goals, \
            three-pointers, and free throws.
              PER              — Player Efficiency Rating: a per-minute measure of overall statistical contributions, \
            normalized to a league average of 15.
              Usage rate       — The percentage of team possessions a player uses while on the court \
            (via field goal attempts, free throw attempts, or turnovers).
              Net rating       — Point differential per 100 possessions (offensive rating minus defensive rating); \
            higher is better.
              Flagrant foul    — An excessive or violent foul; may award free throws, possession, and possible \
            ejection (Flagrant 1 vs. Flagrant 2).
              Technical foul   — A foul for unsportsmanlike conduct not involving physical player contact \
            (e.g., arguing with officials, taunting); results in a free throw for the opponent.
            """;

    private final String originalUserMessage;
    private final LocalDateTime currentDateTime;
    private final String focusPlayerName;

    // Written sequentially before the parallel block
    private volatile SportsQueryIntent sportsQueryIntent;
    private volatile List<EspnTeamProfile> resolvedTeams;

    // volatile: written by parallel steps from different threads
    private volatile String espnScheduleData;
    private volatile String espnRosterData;
    private volatile String espnStatsData;
    private volatile String espnStandingsData;
    private volatile String newsSearchSummary;

    // Written by post-search sequential steps
    private volatile String rosterValidationSummary;
    private volatile String deepPlayerAnalysisSummary;
    private volatile String finalAnalysis;

    private volatile SportsWorkflowStage currentStage;
    private volatile WorkflowOutcome workflowStatus;
    private volatile WorkflowExecutionContext executionContext;

    public SportsResearchWorkflowContext(String originalUserMessage, LocalDateTime currentDateTime, String focusPlayerName) {
        this.originalUserMessage = Objects.requireNonNull(originalUserMessage, "originalUserMessage must not be null");
        this.currentDateTime = Objects.requireNonNull(currentDateTime, "currentDateTime must not be null");
        this.focusPlayerName = focusPlayerName;
        this.currentStage = SportsWorkflowStage.INITIALIZING;
        this.workflowStatus = WorkflowOutcome.COMPLETED;
        this.espnScheduleData = "No schedule data fetched.";
        this.espnRosterData = "No roster data fetched.";
        this.espnStatsData = "No statistics data fetched.";
        this.espnStandingsData = "No standings data fetched.";
        this.rosterValidationSummary = "Roster validation not yet performed.";
        this.deepPlayerAnalysisSummary = null;
    }

    public String getOriginalUserMessage() {
        return originalUserMessage;
    }

    public LocalDateTime getCurrentDateTime() {
        return currentDateTime;
    }

    public String getFocusPlayerName() {
        return focusPlayerName;
    }

    public SportsQueryIntent getSportsQueryIntent() {
        return sportsQueryIntent;
    }

    public void setSportsQueryIntent(SportsQueryIntent sportsQueryIntent) {
        this.sportsQueryIntent = sportsQueryIntent;
    }

    public List<EspnTeamProfile> getResolvedTeams() {
        return resolvedTeams;
    }

    public void setResolvedTeams(List<EspnTeamProfile> resolvedTeams) {
        this.resolvedTeams = resolvedTeams;
    }

    public String getEspnScheduleData() {
        return espnScheduleData;
    }

    public void setEspnScheduleData(String espnScheduleData) {
        this.espnScheduleData = espnScheduleData;
    }

    public String getEspnRosterData() {
        return espnRosterData;
    }

    public void setEspnRosterData(String espnRosterData) {
        this.espnRosterData = espnRosterData;
    }

    public String getEspnStatsData() {
        return espnStatsData;
    }

    public void setEspnStatsData(String espnStatsData) {
        this.espnStatsData = espnStatsData;
    }

    public String getEspnStandingsData() {
        return espnStandingsData;
    }

    public void setEspnStandingsData(String espnStandingsData) {
        this.espnStandingsData = espnStandingsData;
    }

    public String getNewsSearchSummary() {
        return newsSearchSummary;
    }

    public void setNewsSearchSummary(String newsSearchSummary) {
        this.newsSearchSummary = newsSearchSummary;
    }

    public String getRosterValidationSummary() {
        return rosterValidationSummary;
    }

    public void setRosterValidationSummary(String rosterValidationSummary) {
        this.rosterValidationSummary = rosterValidationSummary;
    }

    public String getDeepPlayerAnalysisSummary() {
        return deepPlayerAnalysisSummary;
    }

    public void setDeepPlayerAnalysisSummary(String deepPlayerAnalysisSummary) {
        this.deepPlayerAnalysisSummary = deepPlayerAnalysisSummary;
    }

    public String getFinalAnalysis() {
        return finalAnalysis;
    }

    public void setFinalAnalysis(String finalAnalysis) {
        this.finalAnalysis = finalAnalysis;
    }

    public SportsWorkflowStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(SportsWorkflowStage currentStage) {
        this.currentStage = Objects.requireNonNull(currentStage, "currentStage must not be null");
    }

    public WorkflowOutcome getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(WorkflowOutcome workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public WorkflowExecutionContext getExecutionContext() {
        return executionContext;
    }

    public void setExecutionContext(WorkflowExecutionContext executionContext) {
        this.executionContext = executionContext;
    }
}
