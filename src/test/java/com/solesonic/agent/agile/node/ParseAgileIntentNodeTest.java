package com.solesonic.agent.agile.node;

import com.solesonic.agent.agile.AgileQueryIntent;
import com.solesonic.agent.agile.AgileState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the {@code agile_workflow} failure that reached the calling model as the
 * bare text {@code "null"} and sent it into a retry loop.
 *
 * <p>Spring AI's MCP tool callback renders the caller-visible error from the message of the
 * <em>deepest</em> cause, so these tests assert on the deepest cause rather than on the exception
 * that {@code apply} happens to return.
 */
@ExtendWith(MockitoExtension.class)
class ParseAgileIntentNodeTest {

    private static final String USER_QUESTION = "how many issues are in the current sprint";

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    /**
     * Mirrors Spring AI's {@code AbstractMcpToolMethodCallback#findCauseUsingPlainJava}.
     */
    private static Throwable deepestCause(Throwable throwable) {
        Throwable rootCause = throwable;

        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }

        return rootCause;
    }

    private static Resource template(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    private static AgileState stateWithUserMessage() {
        return new AgileState(Map.of(AgileState.USER_MESSAGE, USER_QUESTION));
    }

    private void stubChatClientReturning(AgileQueryIntent agileQueryIntent) {
        ResponseEntity<ChatResponse, AgileQueryIntent> responseEntity =
                new ResponseEntity<>(null, agileQueryIntent);

        when(chatClient.prompt(any(Prompt.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.responseEntity(AgileQueryIntent.class)).thenReturn(responseEntity);
    }

    /**
     * The production failure. StringTemplate rejects the JSON example, throwing an
     * {@code STException} that carries no message; before the fix that message-less exception was
     * the deepest cause and the caller received the literal text {@code "null"}.
     */
    @Test
    void apply_templateThatCannotCompile_failsWithANonNullRootCauseMessage() {
        Resource brokenTemplate = template("""
                Return a single JSON object:
                  {"issueKeys": ["IB-123"], "userIntent": "TRANSITION"}
                """);
        ParseAgileIntentNode node = new ParseAgileIntentNode(chatClient, brokenTemplate);

        CompletableFuture<Map<String, Object>> result = node.apply(stateWithUserMessage());

        assertThat(result).isCompletedExceptionally();

        Throwable rootCause = deepestCause(catchThrowable(result::join));

        assertThat(rootCause.getMessage())
                .as("the calling model is shown this text; a null here is what caused the retry loop")
                .isNotNull()
                .contains("Parsing the agile query intent failed")
                .contains("The template string is not valid.");
    }

    /**
     * The story's suspected path. {@code assert} is a no-op without {@code -ea}, so a null entity
     * used to fall through into {@code Map.of} and throw a message-less NullPointerException.
     */
    @Test
    void apply_modelReturnsNoEntity_failsWithADescriptiveMessage() {
        stubChatClientReturning(null);
        ParseAgileIntentNode node = new ParseAgileIntentNode(chatClient, template("Parse the request."));

        CompletableFuture<Map<String, Object>> result = node.apply(stateWithUserMessage());

        assertThat(result).isCompletedExceptionally();

        Throwable rootCause = deepestCause(catchThrowable(result::join));

        assertThat(rootCause.getMessage())
                .isNotNull()
                .contains("returned no parseable AgileQueryIntent")
                .contains(USER_QUESTION);
    }

    @Test
    void apply_stateWithoutAUserMessage_failsWithADescriptiveMessage() {
        ParseAgileIntentNode node = new ParseAgileIntentNode(chatClient, template("Parse the request."));

        CompletableFuture<Map<String, Object>> result = node.apply(new AgileState(Map.of()));

        assertThat(result).isCompletedExceptionally();

        Throwable rootCause = deepestCause(catchThrowable(result::join));

        assertThat(rootCause.getMessage())
                .isNotNull()
                .contains("carries no user message");
    }

    @Test
    void apply_modelReturnsAnIntent_putsItOnTheState() throws Exception {
        AgileQueryIntent agileQueryIntent = new AgileQueryIntent(
                List.of(), null, null, null, "", "COUNT", null, null);
        stubChatClientReturning(agileQueryIntent);
        ParseAgileIntentNode node = new ParseAgileIntentNode(chatClient, template("Parse the request."));

        Map<String, Object> result = node.apply(stateWithUserMessage()).get();

        assertThat(result).containsExactly(Map.entry(AgileState.AGILE_QUERY_INTENT, agileQueryIntent));
    }
}
