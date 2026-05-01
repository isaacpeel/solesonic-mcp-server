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



    private final String originalUserMessage;
    private final LocalDateTime currentDateTime;

    // volatile: written by parallel steps from different threads
    private volatile SportsQueryIntent sportsQueryIntent;
    private volatile List<EspnTeamProfile> resolvedTeams;
    private volatile String focusPlayerName;
    private volatile String scheduleSearchSummary;
    private volatile String newsSearchSummary;
    private volatile String statisticsSearchSummary;
    private volatile String espnStandingsData;
    private volatile String espnStatsData;
    private volatile String espnRosterData;
    private volatile String espnScheduleData;
    private volatile String rosterValidationSummary;
    private volatile String deepPlayerAnalysisSummary;
    private volatile String finalAnalysis;

    private volatile SportsWorkflowStage currentStage;
    private volatile WorkflowOutcome workflowStatus;
    private volatile WorkflowExecutionContext executionContext;

    public SportsResearchWorkflowContext(String originalUserMessage, LocalDateTime currentDateTime) {
        this.originalUserMessage = Objects.requireNonNull(originalUserMessage, "originalUserMessage must not be null");
        this.currentDateTime = Objects.requireNonNull(currentDateTime, "currentDateTime must not be null");
        this.currentStage = SportsWorkflowStage.INITIALIZING;
        this.workflowStatus = WorkflowOutcome.COMPLETED;
        this.statisticsSearchSummary = "Statistics not applicable for this query type.";
    }

    public String getOriginalUserMessage() {
        return originalUserMessage;
    }

    public LocalDateTime getCurrentDateTime() {
        return currentDateTime;
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

    public String getFocusPlayerName() {
        return focusPlayerName;
    }

    public void setFocusPlayerName(String focusPlayerName) {
        this.focusPlayerName = focusPlayerName;
    }

    public String getScheduleSearchSummary() {
        return scheduleSearchSummary;
    }

    public void setScheduleSearchSummary(String scheduleSearchSummary) {
        this.scheduleSearchSummary = scheduleSearchSummary;
    }

    public String getNewsSearchSummary() {
        return newsSearchSummary;
    }

    public void setNewsSearchSummary(String newsSearchSummary) {
        this.newsSearchSummary = newsSearchSummary;
    }

    public String getStatisticsSearchSummary() {
        return statisticsSearchSummary;
    }

    public void setStatisticsSearchSummary(String statisticsSearchSummary) {
        this.statisticsSearchSummary = statisticsSearchSummary;
    }

    public String getEspnStandingsData() {
        return espnStandingsData;
    }

    public void setEspnStandingsData(String espnStandingsData) {
        this.espnStandingsData = espnStandingsData;
    }

    public String getEspnStatsData() {
        return espnStatsData;
    }

    public void setEspnStatsData(String espnStatsData) {
        this.espnStatsData = espnStatsData;
    }

    public String getEspnRosterData() {
        return espnRosterData;
    }

    public void setEspnRosterData(String espnRosterData) {
        this.espnRosterData = espnRosterData;
    }

    public String getEspnScheduleData() {
        return espnScheduleData;
    }

    public void setEspnScheduleData(String espnScheduleData) {
        this.espnScheduleData = espnScheduleData;
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
