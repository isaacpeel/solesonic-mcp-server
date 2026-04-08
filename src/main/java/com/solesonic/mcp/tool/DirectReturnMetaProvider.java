package com.solesonic.mcp.tool;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

import java.util.Map;

import static com.solesonic.mcp.tool.atlassian.JiraIssueTools.CREATE_JIRA_ISSUE;

public class DirectReturnMetaProvider implements MetaProvider {
    @Override
    public Map<String, Object> getMeta() {
        return Map.of(
                "returnDirect", true,
                "command", "create-jira",
                "task", CREATE_JIRA_ISSUE);
    }
}
