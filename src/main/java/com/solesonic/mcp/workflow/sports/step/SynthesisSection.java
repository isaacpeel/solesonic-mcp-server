package com.solesonic.mcp.workflow.sports.step;

import static com.solesonic.mcp.prompt.PromptConstants.NBA_TERMINOLOGY_CONTENT;

public enum SynthesisSection {
    SCHEDULE(
        "=============================================\n" +
        "SCHEDULE SEARCH RESULTS\n" +
        "=============================================\n" +
        "{scheduleResults}\n\n",
        "scheduleResults"
    ),
    NEWS(
        "=============================================\n" +
        "RECENT NEWS AND INJURY REPORTS\n" +
        "=============================================\n" +
        "{newsResults}\n\n",
        "newsResults"
    ),
    STATS(
        "=============================================\n" +
        "STATISTICS AND PERFORMANCE DATA\n" +
        "=============================================\n" +
        "{statsResults}\n\n",
        "statsResults"
    ),
    TERMINOLOGY(
        "=============================================\n" +
        "NBA TERMINOLOGY\n" +
        "=============================================\n" +
        NBA_TERMINOLOGY_CONTENT + "\n",
        null
    );

    private final String fragmentTemplate;
    private final String variableKey;

    SynthesisSection(String fragmentTemplate, String variableKey) {
        this.fragmentTemplate = fragmentTemplate;
        this.variableKey = variableKey;
    }

    public String getFragmentTemplate() {
        return fragmentTemplate;
    }

    public boolean hasVariable() {
        return variableKey != null;
    }

    public String getVariableKey() {
        return variableKey;
    }
}
