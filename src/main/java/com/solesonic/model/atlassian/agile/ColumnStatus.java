package com.solesonic.model.atlassian.agile;

import java.io.Serializable;

public record ColumnStatus(String id,
                            String self) implements Serializable {
}
