package com.solesonic.agent.sports.model;

import java.util.List;

public record SportsEntityExtraction(
        List<String> teams,
        List<String> players,
        String timeContext,
        List<SportsQuestionType> questionTypes
) {}
