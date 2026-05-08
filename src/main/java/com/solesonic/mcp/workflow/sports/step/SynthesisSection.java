package com.solesonic.mcp.workflow.sports.step;

import static com.solesonic.mcp.prompt.PromptConstants.NBA_TERMINOLOGY_CONTENT;

public enum SynthesisSection {
    SCHEDULE(
            """
                    =============================================
                    SCHEDULE SEARCH RESULTS
                    =============================================
                    {scheduleResults}
                    
                    """,
        "scheduleResults"
    ),
    NEWS(
            """
                    =============================================
                    RECENT NEWS AND INJURY REPORTS
                    =============================================
                    {newsResults}
                    
                    """,
        "newsResults"
    ),
    STATS(
            """
                    =============================================
                    STATISTICS AND PERFORMANCE DATA
                    =============================================
                    {statsResults}
                    
                    """,
        "statsResults"
    ),
    ROSTER(
            """
                    =============================================
                    CURRENT TEAM ROSTERS (ESPN)
                    =============================================
                    {rosterData}

                    """,
        "rosterData"
    ),
    TERMINOLOGY(
            """
                =============================================
                NBA TERMINOLOGY
                =============================================
                """ +
        "\n"+NBA_TERMINOLOGY_CONTENT + "\n",
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
