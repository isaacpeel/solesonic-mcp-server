package com.solesonic.mcp.config.atlassian;

import com.solesonic.mcp.exception.atlassian.AtlassianErrorMessages;
import com.solesonic.mcp.exception.atlassian.JiraException;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns every non-2xx Atlassian response into a {@link JiraException} carrying the real status and
 * body, before any caller tries to decode it as a success payload.
 * <p>
 * The Atlassian services use {@code exchangeToMono}, which bypasses WebClient's default status
 * handlers, so without this an error body (often plain text) falls through to the JSON decoder
 * and surfaces as an unrelated {@code DecodingException}.
 */
@Component
public class AtlassianErrorResponseFilter implements ExchangeFilterFunction {

    private final JsonMapper jsonMapper;

    public AtlassianErrorResponseFilter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    @Nonnull
    public Mono<ClientResponse> filter(@Nonnull ClientRequest request, @Nonnull ExchangeFunction next) {
        return next.exchange(request).flatMap(response -> {
            if (!response.statusCode().isError()) {
                return Mono.just(response);
            }

            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(new JiraException(
                            "Atlassian %s %s failed with %s: %s".formatted(
                                    request.method().name(),
                                    request.url().getPath(),
                                    response.statusCode(),
                                    AtlassianErrorMessages.summarize(jsonMapper, body)),
                            body)));
        });
    }
}
