package com.solesonic.model.atlassian.agile;

import java.io.Serializable;
import java.util.List;

public record BoardColumn(String name,
                           List<ColumnStatus> statuses) implements Serializable {
}
