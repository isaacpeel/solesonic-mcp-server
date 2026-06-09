package com.solesonic.model.atlassian.agile;

import java.io.Serializable;

public record BoardIssue(String id,
                         String self,
                         String key) implements Serializable {
}
