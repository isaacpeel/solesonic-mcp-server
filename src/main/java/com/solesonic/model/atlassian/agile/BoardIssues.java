package com.solesonic.model.atlassian.agile;

import java.io.Serializable;
import java.util.List;

public record BoardIssues(String expand, Integer startAt, Integer maxResults, Integer total, List<BoardIssue> issues) implements Serializable {
}
