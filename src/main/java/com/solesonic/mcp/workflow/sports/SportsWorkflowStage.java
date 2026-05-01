package com.solesonic.mcp.workflow.sports;

public enum SportsWorkflowStage {
    INITIALIZING,
    PARSING_INTENT,
    SEARCHING_SCHEDULE,
    SEARCHING_NEWS,
    SEARCHING_STATISTICS,
    SYNTHESIZING_ANALYSIS,
    COMPLETED,
    FAILED
}
