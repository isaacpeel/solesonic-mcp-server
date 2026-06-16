package com.solesonic.model.atlassian.agile;

import java.io.Serializable;

public record Board(Integer id, String self, String name, String type) implements Serializable {
}
