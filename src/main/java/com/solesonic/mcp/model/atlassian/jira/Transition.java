package com.solesonic.mcp.model.atlassian.jira;

public record Transition(String id, String name, Status to) {}
