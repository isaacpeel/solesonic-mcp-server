package com.solesonic.model.atlassian.jira;

public record Transition(String id, String name, Status to) {}
