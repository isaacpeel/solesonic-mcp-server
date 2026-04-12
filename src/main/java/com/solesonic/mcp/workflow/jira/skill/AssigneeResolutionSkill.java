package com.solesonic.mcp.workflow.jira.skill;

import com.solesonic.mcp.workflow.framework.WorkflowProgressTracker;
import com.solesonic.mcp.workflow.model.AssigneeLookupResult;
import com.solesonic.mcp.workflow.service.AssigneeResolutionService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AssigneeResolutionSkill {
    private final AssigneeResolutionService assigneeResolutionService;

    public AssigneeResolutionSkill(AssigneeResolutionService assigneeResolutionService) {
        this.assigneeResolutionService = assigneeResolutionService;
    }

    public Mono<AssigneeLookupResult> resolve(String rawUserRequest, WorkflowProgressTracker.StepProgress stepProgress) {
        return assigneeResolutionService.resolve(rawUserRequest, stepProgress);
    }
}
