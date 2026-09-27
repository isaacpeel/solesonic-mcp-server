package com.solesonic.model.atlassian.agile;

import java.io.Serializable;
import java.util.List;

public record ColumnConfig(List<BoardColumn> columns,
                            String constraintType) implements Serializable {
}
