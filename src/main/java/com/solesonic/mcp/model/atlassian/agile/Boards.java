package com.solesonic.mcp.model.atlassian.agile;

import java.io.Serializable;
import java.util.List;

public record Boards(List<Board> values, Integer maxResults, Integer total, boolean isLast) implements Serializable {
}
