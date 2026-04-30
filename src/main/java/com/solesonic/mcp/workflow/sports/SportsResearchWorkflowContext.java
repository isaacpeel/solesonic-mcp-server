package com.solesonic.mcp.workflow.sports;

import com.solesonic.mcp.workflow.framework.WorkflowContext;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;

import java.time.LocalDateTime;
import java.util.Objects;

@SuppressWarnings("unused")
public class SportsResearchWorkflowContext implements WorkflowContext {

    private final String originalUserMessage;
    private final LocalDateTime currentDateTime;

    // volatile: written by parallel steps from different threads
    private volatile SportsQueryIntent sportsQueryIntent;
    private volatile String scheduleSearchSummary;
    private volatile String newsSearchSummary;
    private volatile String statisticsSearchSummary;
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
