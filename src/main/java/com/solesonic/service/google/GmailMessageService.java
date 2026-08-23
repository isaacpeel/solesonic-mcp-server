package com.solesonic.service.google;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.mcp.exception.google.GmailLabelNotFoundException;
import com.solesonic.model.google.gmail.GmailHeader;
import com.solesonic.model.google.gmail.GmailLabel;
import com.solesonic.model.google.gmail.GmailLabelList;
import com.solesonic.model.google.gmail.GmailMessageList;
import com.solesonic.model.google.gmail.GmailMessageMetadata;
import com.solesonic.model.google.gmail.GmailMessagePart;
import com.solesonic.model.google.gmail.GmailMessageRef;
import com.solesonic.model.google.gmail.GmailMessageSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static com.solesonic.mcp.config.google.GoogleConstants.GOOGLE_API_WEB_CLIENT;
import static com.solesonic.service.google.GmailConstants.*;

@Service
public class GmailMessageService {
    private static final Logger log = LoggerFactory.getLogger(GmailMessageService.class);

    private final WebClient webClient;

    public GmailMessageService(@Qualifier(GOOGLE_API_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * The most recent inbox messages, newest first. Gmail's list endpoint returns ids and nothing
     * else, so each subject costs its own request.
     */
    public List<GmailMessageSummary> listInboxMessages(int maxResults) {
        log.info("Listing the {} most recent inbox messages", maxResults);

        return listMessages(INBOX_LABEL, maxResults);
    }

    /**
     * The most recent messages under a caller-supplied label, newest first. Gmail's label ids and
     * display names only coincide for system labels, so a user-created label name is resolved to
     * its id first.
     */
    public List<GmailMessageSummary> listMessagesByLabel(String label, int maxResults) {
        log.info("Listing the {} most recent messages labeled '{}'", maxResults, label);

        String labelId = resolveLabelId(label);

        return listMessages(labelId, maxResults);
    }

    /** A single message by id, for callers that already have one — e.g. resolving "email number N" from a prior list. */
    public GmailMessageSummary getMessageSummary(String messageId) {
        log.info("Retrieving message summary for id {}", messageId);

        GmailMessageMetadata metadata = messageMetadata(messageId);

        return summarize(metadata);
    }

    private List<GmailMessageSummary> listMessages(String labelId, int maxResults) {
        GmailMessageList messageList = listMessageIds(labelId, maxResults);

        if (messageList == null || messageList.messages() == null || messageList.messages().isEmpty()) {
            log.info("No messages returned for label '{}'", labelId);

            return List.of();
        }

        List<GmailMessageSummary> summaries = new ArrayList<>();

        // Deliberately sequential. A reactive fan-out would subscribe each request on a Reactor
        // thread, and those threads do not carry the inheritable security context that
        // GoogleRequestAuthorizationFilter reads the caller's JWT from — the requests would go out
        // with no Authorization header and come back 401.
        for (GmailMessageRef messageRef : messageList.messages()) {
            GmailMessageMetadata metadata = messageMetadata(messageRef.id());

            if (metadata != null) {
                summaries.add(summarize(metadata));
            }
        }

        log.info("Summarized {} messages for label '{}'", summaries.size(), labelId);

        return summaries;
    }

    private GmailMessageList listMessageIds(String labelId, int maxResults) {
        String[] basePathSegments = {GMAIL_PATH, VERSION_PATH, USERS_PATH, ME, MESSAGES_PATH};

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .queryParam(LABEL_IDS_PARAM, labelId)
                        .queryParam(MAX_RESULTS_PARAM, maxResults)
                        .build())
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(errorBody -> Mono.error(new GmailException(
                                        "Failed to list messages for label '%s': %s".formatted(labelId, response.statusCode()),
                                        errorBody)));
                    }

                    return response.bodyToMono(GmailMessageList.class);
                })
                .block();
    }

    /** Matched case-insensitively against the label's display name — callers don't know opaque ids like {@code Label_15}. */
    private String resolveLabelId(String label) {
        String[] basePathSegments = {GMAIL_PATH, VERSION_PATH, USERS_PATH, ME, LABELS_PATH};

        GmailLabelList labelList = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(errorBody -> Mono.error(new GmailException(
                                        "Failed to list labels: %s".formatted(response.statusCode()),
                                        errorBody)));
                    }

                    return response.bodyToMono(GmailLabelList.class);
                })
                .block();

        List<GmailLabel> labels = labelList == null || labelList.labels() == null
                ? List.of()
                : labelList.labels();

        return labels.stream()
                .filter(candidate -> label.equalsIgnoreCase(candidate.name()))
                .map(GmailLabel::id)
                .findFirst()
                .orElseThrow(() -> new GmailLabelNotFoundException("No Gmail label named '%s' was found".formatted(label)));
    }

    private GmailMessageMetadata messageMetadata(String messageId) {
        String[] basePathSegments = {GMAIL_PATH, VERSION_PATH, USERS_PATH, ME, MESSAGES_PATH, messageId};

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .queryParam(FORMAT_PARAM, METADATA_FORMAT)
                        .queryParam(METADATA_HEADERS_PARAM, SUBJECT_HEADER, FROM_HEADER, DATE_HEADER)
                        .build())
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(errorBody -> Mono.error(new GmailException(
                                        "Failed to read message %s: %s".formatted(messageId, response.statusCode()),
                                        errorBody)));
                    }

                    return response.bodyToMono(GmailMessageMetadata.class);
                })
                .block();
    }

    private GmailMessageSummary summarize(GmailMessageMetadata metadata) {
        List<GmailHeader> headers = headers(metadata);

        return new GmailMessageSummary(
                metadata.id(),
                header(headers, SUBJECT_HEADER, NO_SUBJECT),
                header(headers, FROM_HEADER, UNKNOWN_SENDER),
                header(headers, DATE_HEADER, ""));
    }

    private List<GmailHeader> headers(GmailMessageMetadata metadata) {
        GmailMessagePart payload = metadata.payload();

        if (payload == null || payload.headers() == null) {
            return List.of();
        }

        return payload.headers();
    }

    /** Header names are matched case-insensitively — RFC 5322 does not require a particular casing. */
    private String header(List<GmailHeader> headers, String name, String fallback) {
        return headers.stream()
                .filter(header -> name.equalsIgnoreCase(header.name()))
                .map(GmailHeader::value)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallback);
    }
}
