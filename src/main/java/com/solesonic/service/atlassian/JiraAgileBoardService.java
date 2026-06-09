package com.solesonic.service.atlassian;

import com.solesonic.model.atlassian.agile.BoardIssues;
import com.solesonic.model.atlassian.jira.IssueStatus;
import com.solesonic.model.atlassian.jira.JiraIssue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.service.atlassian.AtlassianConstants.*;

@Service
public class JiraAgileBoardService {
    public static final String JQL = "jql";
    private final WebClient webClient;

    @Value("${solesonic.llm.jira.cloud.id.path}")
    private String cloudIdPath;

    private final JiraIssueService jiraIssueService;

    public JiraAgileBoardService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient,
                                 JiraIssueService jiraIssueService) {
        this.webClient = webClient;
        this.jiraIssueService = jiraIssueService;
    }

    public List<JiraIssue> findByStaus(List<IssueStatus> issueStatuses, String boardId) {
        String[] base = {EX, JIRA, cloudIdPath, REST_PATH, AGILE_PATH, AGILE_VERSION_PATH, BOARD_PATH, boardId};

        String statuses = issueStatuses.stream()
                .map(IssueStatus::toJqlLabel)
                .collect(Collectors.joining("\",\""));

        String jql = "status IN("+statuses+")";

        BoardIssues boardIssues = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(base).queryParam(JQL, jql).build())
                .exchangeToMono(response -> response.bodyToMono(BoardIssues.class))
                .block();

        List<JiraIssue> jiraIssues = new ArrayList<>();

        assert boardIssues != null;

        boardIssues.issues()
                .forEach(boardIssue -> {
                    JiraIssue jiraIssue = jiraIssueService.get(boardIssue.key());
                    jiraIssues.add(jiraIssue);
                });

        return jiraIssues;
    }
}
