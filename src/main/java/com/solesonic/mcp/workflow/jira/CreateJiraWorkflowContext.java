package com.solesonic.mcp.workflow.jira;

import com.solesonic.mcp.workflow.framework.WorkflowContext;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import com.solesonic.mcp.workflow.framework.WorkflowPendingInput;
import com.solesonic.mcp.workflow.framework.WorkflowQuestion;
import com.solesonic.mcp.workflow.model.AssigneeLookupResult;
import com.solesonic.mcp.workflow.model.JiraIssueCreatePayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unused")
public class CreateJiraWorkflowContext implements WorkflowContext {
    private final String originalUserMessage;

    // volatile: written by parallel steps from different threads
    private volatile String storySummary;
    private volatile String detailedDescription;
    private volatile List<String> acceptanceCriteria;
    private volatile AssigneeLookupResult assigneeLookupResult;
    private volatile WorkflowStage currentStage;

    private JiraIssueCreatePayload finalPayload;
    private boolean payloadValidated;
    private WorkflowOutcome workflowStatus;
    private boolean requiresUserInput;
    private List<WorkflowQuestion> clarificationQuestions;
    private List<String> unresolvedFields;
    private Map<String, Object> pendingAnswers;
    private WorkflowPendingInput pendingInput;

    public CreateJiraWorkflowContext(String originalUserMessage) {
        this.originalUserMessage = Objects.requireNonNull(originalUserMessage, "originalUserMessage must not be null");
        this.acceptanceCriteria = new ArrayList<>();
        this.workflowStatus = WorkflowOutcome.COMPLETED;
        this.clarificationQuestions = new ArrayList<>();
        this.unresolvedFields = new ArrayList<>();
        this.pendingAnswers = new HashMap<>();
        this.currentStage = WorkflowStage.INITIALIZING;
    }

    public String getOriginalUserMessage() {
        return originalUserMessage;
    }

    public String getStorySummary() {
        return storySummary;
    }

    public void setStorySummary(String storySummary) {
        this.storySummary = storySummary;
    }

    public String getDetailedDescription() {
        return detailedDescription;
    }

    public void setDetailedDescription(String detailedDescription) {
        this.detailedDescription = detailedDescription;
    }

    public List<String> getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(List<String> acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria == null ? new ArrayList<>() : new ArrayList<>(acceptanceCriteria);
    }

    public AssigneeLookupResult getAssigneeLookupResult() {
        return assigneeLookupResult;
    }

    public void setAssigneeLookupResult(AssigneeLookupResult assigneeLookupResult) {
        this.assigneeLookupResult = assigneeLookupResult;
    }

    public JiraIssueCreatePayload getFinalPayload() {
        return finalPayload;
    }

    public void setFinalPayload(JiraIssueCreatePayload finalPayload) {
        this.finalPayload = finalPayload;
    }

    public boolean isPayloadValidated() {
        return payloadValidated;
    }

    public void setPayloadValidated(boolean payloadValidated) {
        this.payloadValidated = payloadValidated;
    }

    public WorkflowOutcome getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(WorkflowOutcome workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public boolean isRequiresUserInput() {
        return requiresUserInput;
    }

    public void setRequiresUserInput(boolean requiresUserInput) {
        this.requiresUserInput = requiresUserInput;
    }

    public List<WorkflowQuestion> getClarificationQuestions() {
        return clarificationQuestions;
    }

    public void setClarificationQuestions(List<WorkflowQuestion> clarificationQuestions) {
        this.clarificationQuestions = clarificationQuestions == null ? new ArrayList<>() : new ArrayList<>(clarificationQuestions);
    }

    public List<String> getUnresolvedFields() {
        return unresolvedFields;
    }

    public void setUnresolvedFields(List<String> unresolvedFields) {
        this.unresolvedFields = unresolvedFields == null ? new ArrayList<>() : new ArrayList<>(unresolvedFields);
    }

    public Map<String, Object> getPendingAnswers() {
        return pendingAnswers;
    }

    public void setPendingAnswers(Map<String, Object> pendingAnswers) {
        this.pendingAnswers = pendingAnswers == null ? new HashMap<>() : new HashMap<>(pendingAnswers);
    }

    public WorkflowStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(WorkflowStage currentStage) {
        this.currentStage = Objects.requireNonNull(currentStage, "currentStage must not be null");
    }

    public WorkflowPendingInput getPendingInput() {
        return pendingInput;
    }

    public void setPendingInput(WorkflowPendingInput pendingInput) {
        this.pendingInput = pendingInput;
    }
}
