package com.solesonic.mcp.command;

import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;

import java.util.List;
import java.util.Map;

import static com.solesonic.mcp.prompt.PromptProvider.COMMAND;
import static com.solesonic.mcp.tool.atlassian.JiraIssueTools.CREATE_JIRA_ISSUE;

public class CreateJiraCommandProvider extends DefaultMetaProvider {

    public static final String JIRA = "jira";
    public static final String TOOLS = "tools";

    @Override
    public Map<String, Object> getMeta() {
        return Map.of(COMMAND, JIRA, TOOLS, List.of(CREATE_JIRA_ISSUE));
    }
}
