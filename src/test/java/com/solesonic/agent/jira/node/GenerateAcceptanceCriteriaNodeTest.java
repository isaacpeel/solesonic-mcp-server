package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateAcceptanceCriteriaNodeTest {

    private static final String USER_MESSAGE = "Create a login story";
    private static final String DETAILED_DESCRIPTION = "As a user, I want to log in, so that I can access my account";
    private static final String STORY_SUMMARY = "Add login capability";

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private GenerateAcceptanceCriteriaNode node;

    @BeforeEach
    void setUp() {
        ByteArrayResource template = new ByteArrayResource(
                "Story: {user_story}\nRequest: {user_request}\nFormat: {format}".getBytes(StandardCharsets.UTF_8));
        node = new GenerateAcceptanceCriteriaNode(chatClient, template);
    }

    private static JiraState stateWithDescriptionAndSummary() {
        return new JiraState(Map.of(
                JiraState.USER_MESSAGE, USER_MESSAGE,
                JiraState.DETAILED_DESCRIPTION, DETAILED_DESCRIPTION,
                JiraState.STORY_SUMMARY, STORY_SUMMARY));
    }

    @Test
    void apply_rendersThePromptFromTheDetailedDescriptionNotTheSummary() throws Exception {
        String modelResponse = """
                Given valid credentials, when I log in, then I am authenticated
                Given invalid credentials, when I log in, then I see an error""";

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatClient.prompt(promptCaptor.capture())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.options(any())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(modelResponse);

        Map<String, Object> updates = node.apply(stateWithDescriptionAndSummary()).get();

        assertThat(updates).containsEntry(JiraState.ACCEPTANCE_CRITERIA, List.of(
                "Given valid credentials, when I log in, then I am authenticated",
                "Given invalid credentials, when I log in, then I see an error"));

        String renderedPrompt = promptCaptor.getValue().getContents();
        assertThat(renderedPrompt).contains(DETAILED_DESCRIPTION);
        assertThat(renderedPrompt).doesNotContain(STORY_SUMMARY);
    }

    @Test
    void apply_criterionContainingACommaSurvivesAsOneEntry() throws Exception {
        String criterionWithCommas = "Given a comma, in the criterion, when parsed, then it stays one line";

        when(chatClient.prompt(any(Prompt.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.options(any())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(criterionWithCommas);

        Map<String, Object> updates = node.apply(stateWithDescriptionAndSummary()).get();

        assertThat(updates).containsEntry(JiraState.ACCEPTANCE_CRITERIA, List.of(criterionWithCommas));
    }

    @Test
    void apply_missingDetailedDescription_fails() {
        JiraState state = new JiraState(Map.of(JiraState.USER_MESSAGE, USER_MESSAGE, JiraState.STORY_SUMMARY, STORY_SUMMARY));

        CompletableFuture<Map<String, Object>> result = node.apply(state);

        assertThat(result).isCompletedExceptionally();
    }
}
