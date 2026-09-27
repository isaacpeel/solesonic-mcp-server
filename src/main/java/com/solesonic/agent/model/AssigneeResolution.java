package com.solesonic.agent.model;

import java.util.List;

/**
 * Outcome of looking up the assignee named in a story request. Only {@link Resolved} carries an
 * assignee; every other outcome means the user has to pick one before the story can be created.
 */
public sealed interface AssigneeResolution {

    record Resolved(AssigneeLookupResult assigneeLookupResult) implements AssigneeResolution {
    }

    record Ambiguous(String searchTerm, List<AssigneeCandidate> candidates) implements AssigneeResolution {
    }

    record NotFound(String searchTerm) implements AssigneeResolution {
    }

    record NotRequested() implements AssigneeResolution {
    }
}
