package com.solesonic.agent.config;

import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DecisionModelClientConfigTest {

    private static final String RESPONSE = """
            {"model": "test-decision-model",
             "answers": {"intent": {"type": "choice", "choice": "RESTYLE_PHOTO", "confidence": 0.9,
                                    "probabilities": {"RESTYLE_PHOTO": 0.9}}}}""";

    /**
     * The chat clients' host already ends in /v1, so the decision model shares it and only the
     * System One path is appended.
     */
    @Test
    void typeSafeApi_postsToSystemOneUnderTheChatHostWithTheBearerKey() {
        assertPostsTo("http://litellm-host:4000/v1");
    }

    @Test
    void typeSafeApi_hostWithATrailingSlash_doesNotDoubleTheSlash() {
        assertPostsTo("http://litellm-host:4000/v1/");
    }

    private static void assertPostsTo(String chatHost) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockRestServiceServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        mockRestServiceServer.expect(requestTo("http://litellm-host:4000/v1/systemone"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        TypeSafeClient typeSafeClient = TypeSafeClient.builder()
                .typeSafeApi(DecisionModelClientConfig.typeSafeApi(chatHost, "test-key", restClientBuilder))
                .defaultModel("test-decision-model")
                .build();

        SystemOneResponse systemOneResponse = typeSafeClient.systemOne("[1 photo attached]",
                Map.of("intent", Choice.of("Classify.", "RESTYLE_PHOTO", "EDIT_PERSON_IN_PHOTO")));

        assertThat(systemOneResponse.choiceValue("intent")).isEqualTo("RESTYLE_PHOTO");
        mockRestServiceServer.verify();
    }
}
