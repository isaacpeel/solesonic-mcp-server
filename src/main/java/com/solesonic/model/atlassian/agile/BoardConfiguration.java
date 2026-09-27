package com.solesonic.model.atlassian.agile;

import java.io.Serializable;

public record BoardConfiguration(Integer id,
                                  String name,
                                  String type,
                                  ColumnConfig columnConfig) implements Serializable {
}
