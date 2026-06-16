package com.solesonic.model.espn;

import java.io.Serializable;

public record EspnStatusType(
        String state,
        String description,
        String shortDetail
) implements Serializable {}
