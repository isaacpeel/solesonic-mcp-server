package com.solesonic.mcp.tool.provider;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptProvider.COMMAND;
import static com.solesonic.mcp.tool.atlassian.JiraIssueTools.CREATE_JIRA_ISSUE;

public class CreateJiraMetaProvider implements MetaProvider {



    @Override
    public Map<String, Object> getMeta() {
        return Map.of(
                ProviderConstants.RETURN_DIRECT, true,
                COMMAND, "create-jira",
                "task", CREATE_JIRA_ISSUE);
    }
}
