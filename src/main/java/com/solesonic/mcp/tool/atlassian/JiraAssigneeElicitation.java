package com.solesonic.mcp.tool.atlassian;

import com.solesonic.agent.model.AssigneeCandidate;
import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.mcp.tool.McpConfirmations;
import com.solesonic.service.atlassian.AssigneeResolutionService;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Asks the user who a Jira story should be assigned to when the request did not settle it. Jira
 * rejects issues without an assignee, so every outcome other than {@link Selection.Selected} means
 * the story must not be created.
 */
@Service
public class JiraAssigneeElicitation {

    private static final Logger log = LoggerFactory.getLogger(JiraAssigneeElicitation.class);

    public static final String ASSIGNEE_ACCOUNT_ID = "assigneeAccountId";
    public static final String USER_SELECTED = "USER_SELECTED";

    private static final String NO_MATCH_MESSAGE =
            "No assignee could be found in the request. Who should this story be assigned to?";
    private static final String AMBIGUOUS_MESSAGE =
            "More than one Jira user matches the requested assignee. Who should this story be assigned to?";

    private final AssigneeResolutionService assigneeResolutionService;

    public JiraAssigneeElicitation(AssigneeResolutionService assigneeResolutionService) {
        this.assigneeResolutionService = assigneeResolutionService;
    }

    public sealed interface Selection {

        record Selected(AssigneeLookupResult assigneeLookupResult) implements Selection {
        }

        record Declined() implements Selection {
        }

        record Cancelled() implements Selection {
        }

        record NoCandidates() implements Selection {
        }

        record InvalidSelection(String selectedAccountId) implements Selection {
        }
    }

    /**
     * @param matchingCandidates the users an ambiguous search matched; empty when nobody matched or
     *                           nobody was named, in which case every assignable user is offered
     */
    public Selection selectAssignee(McpSyncRequestContext context,
                                    List<AssigneeCandidate> matchingCandidates,
                                    Map<String, Object> meta) {
        boolean ambiguous = !matchingCandidates.isEmpty();

        List<AssigneeCandidate> candidates = ambiguous
                ? matchingCandidates
                : assigneeResolutionService.listAssigneeCandidates();

        if (candidates.isEmpty()) {
            log.warn("Cannot ask for an assignee: Jira returned no assignable users. meta={}", meta);
            return new Selection.NoCandidates();
        }

        String message = ambiguous ? AMBIGUOUS_MESSAGE : NO_MATCH_MESSAGE;

        log.info("Asking the user to pick an assignee from {} candidate(s). ambiguous={} meta={}", candidates.size(), ambiguous, meta);

        ElicitResult elicitResult = McpConfirmations.elicit(context, message, assigneeSchema(candidates), meta);

        return switch (elicitResult.action()) {
            case ACCEPT -> selected(elicitResult, candidates);
            case DECLINE -> {
                log.info("User declined to pick an assignee. meta={}", meta);
                yield new Selection.Declined();
            }
            case CANCEL -> {
                log.info("User cancelled the assignee picker. meta={}", meta);
                yield new Selection.Cancelled();
            }
        };
    }

    private static Selection selected(ElicitResult elicitResult, List<AssigneeCandidate> candidates) {
        String selectedAccountId = Optional.ofNullable(elicitResult.content())
                .map(content -> content.get(ASSIGNEE_ACCOUNT_ID))
                .map(Object::toString)
                .orElse(null);

        Optional<AssigneeCandidate> chosen = candidates.stream()
                .filter(candidate -> candidate.accountId().equals(selectedAccountId))
                .findFirst();

        if (chosen.isEmpty()) {
            log.warn("Assignee picker returned an account id that was not offered: {}", selectedAccountId);
            return new Selection.InvalidSelection(selectedAccountId);
        }

        AssigneeCandidate candidate = chosen.get();

        log.info("User picked assignee {} ({})", candidate.label(), candidate.accountId());

        return new Selection.Selected(new AssigneeLookupResult(true, candidate.accountId(), USER_SELECTED, candidate.label()));
    }

    private static Map<String, Object> assigneeSchema(List<AssigneeCandidate> candidates) {
        List<Map<String, Object>> choices = candidates.stream()
                .map(candidate -> Map.<String, Object>of("const", candidate.accountId(), "title", candidate.label()))
                .toList();

        return Map.of(
                "type", "object",
                "properties", Map.of(
                        ASSIGNEE_ACCOUNT_ID, Map.of(
                                "type", "string",
                                "title", "Assignee",
                                "oneOf", choices
                        )
                ),
                "required", List.of(ASSIGNEE_ACCOUNT_ID)
        );
    }
}
