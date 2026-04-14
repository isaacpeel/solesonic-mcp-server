package com.solesonic.mcp.workflow.agile;

import com.solesonic.mcp.model.atlassian.agile.Board;
import com.solesonic.mcp.workflow.framework.WorkflowContext;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public class AgileQueryWorkflowContext implements WorkflowContext {
    private final String originalUserMessage;

    // volatile: written by parallel steps from different threads
    private volatile AgileQueryResult agileQueryResult;
    private volatile List<Board> boards;

    private AgileWorkflowStage currentStage;
    private WorkflowOutcome workflowStatus;

    public AgileQueryWorkflowContext(String originalUserMessage) {
        this.originalUserMessage = Objects.requireNonNull(originalUserMessage, "originalUserMessage must not be null");
        this.boards = new ArrayList<>();
        this.currentStage = AgileWorkflowStage.INITIALIZING;
        this.workflowStatus = WorkflowOutcome.COMPLETED;
    }

    public String getOriginalUserMessage() {
        return originalUserMessage;
    }

    public AgileQueryResult getAgileQueryResult() {
        return agileQueryResult;
    }

    public void setAgileQueryResult(AgileQueryResult agileQueryResult) {
        this.agileQueryResult = agileQueryResult;
    }

    public List<Board> getBoards() {
        return boards;
    }

    public void setBoards(List<Board> boards) {
        this.boards = boards == null ? new ArrayList<>() : new ArrayList<>(boards);
    }

    public AgileWorkflowStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(AgileWorkflowStage currentStage) {
        this.currentStage = Objects.requireNonNull(currentStage, "currentStage must not be null");
    }

    public WorkflowOutcome getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(WorkflowOutcome workflowStatus) {
        this.workflowStatus = workflowStatus;
    }
}
