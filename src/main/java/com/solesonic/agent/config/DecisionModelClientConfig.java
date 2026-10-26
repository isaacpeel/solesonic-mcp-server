package com.solesonic.agent.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.api.TypeSafeApi;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * The decision model client used by the image workflow for intent classification and workflow
 * selection. It talks to the same LiteLLM proxy as the chat clients, through the same
 * {@code /v1} base URL, so the System One path is {@code /systemone} rather than the SDK's default
 * {@code /v1/systemone}. Defining this bean switches off the starter's own client; the key, model,
 * timeout and retries still come from {@code spring.ai.typesafe.*}.
 */
@Configuration
@EnableConfigurationProperties(TypeSafeProperties.class)
public class DecisionModelClientConfig {

    static final String SYSTEM_ONE_PATH = "/systemone";
    static final String MODELS_PATH = "/models";

    @Bean
    public TypeSafeClient typeSafeClient(TypeSafeProperties typeSafeProperties,
                                         ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        String apiKey = typeSafeProperties.getApiKey();
        Assert.state(StringUtils.isNotBlank(apiKey),
                () -> "No API key configured. Set " + TypeSafeProperties.CONFIG_PREFIX + ".api-key.");

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(typeSafeProperties.getTimeout());

        // Cloned, because the context's builder may be shared with other clients.
        RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder)
                .clone()
                .requestFactory(requestFactory);

        return TypeSafeClient.builder()
                .typeSafeApi(typeSafeApi(typeSafeProperties.getBaseUrl(), apiKey, restClientBuilder))
                .defaultModel(typeSafeProperties.getModel())
                // Declared as well as applied to the transport, so retries are budgeted against it.
                .timeout(typeSafeProperties.getTimeout())
                .retryPolicy(typeSafeProperties.toRetryPolicy())
                .build();
    }

    /**
     * Builds the transport over the given builder, leaving its request factory untouched so tests
     * can bind a mock server to it.
     */
    static TypeSafeApi typeSafeApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder) {
        return TypeSafeApi.builder()
                .baseUrl(Strings.CS.removeEnd(baseUrl, "/"))
                .apiKey(apiKey)
                .systemOnePath(SYSTEM_ONE_PATH)
                .modelsPath(MODELS_PATH)
                .restClientBuilder(restClientBuilder)
                .build();
    }
}
