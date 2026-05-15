package com.solesonic.a2a.workflow.jira.skill;

import com.solesonic.a2a.workflow.framework.WorkflowProgressTracker;
import com.solesonic.a2a.workflow.model.AssigneeLookupResult;
import com.solesonic.a2a.workflow.service.AssigneeResolutionService;
import org.springframework.stereotype.Component;

@Component
public class AssigneeResolutionSkill {
    private final AssigneeResolutionService assigneeResolutionService;

    public AssigneeResolutionSkill(AssigneeResolutionService assigneeResolutionService) {
        this.assigneeResolutionService = assigneeResolutionService;
    }

    public AssigneeLookupResult resolve(String rawUserRequest, WorkflowProgressTracker.StepProgress stepProgress) {
        return assigneeResolutionService.resolve(rawUserRequest, stepProgress);
    }
}
