package com.solesonic.service.google;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.mcp.exception.google.GmailLabelNotFoundException;
import com.solesonic.mcp.exception.google.GmailMessageNotFoundException;
import com.solesonic.model.google.gmail.GmailHeader;
import com.solesonic.model.google.gmail.GmailLabel;
import com.solesonic.model.google.gmail.GmailLabelList;
import com.solesonic.model.google.gmail.GmailMessageBody;
import com.solesonic.model.google.gmail.GmailMessageList;
import com.solesonic.model.google.gmail.GmailMessageMetadata;
import com.solesonic.model.google.gmail.GmailMessagePart;
import com.solesonic.model.google.gmail.GmailMessageRef;
import com.solesonic.model.google.gmail.GmailMessageSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.solesonic.mcp.config.google.GoogleConstants.GOOGLE_API_WEB_CLIENT;
import static com.solesonic.service.google.GmailConstants.*;

@Service
public class GmailMessageService {
    private static final Logger log = LoggerFactory.getLogger(GmailMessageService.class);

    /** Preference order when hunting for a message's readable text. */
    private static final List<String> TEXT_MIME_TYPES = List.of(TEXT_PLAIN_MIME_TYPE, TEXT_HTML_MIME_TYPE);

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

    /**
     * The readable text of a single message. Requests {@code format=full} so Gmail returns the whole
     * MIME tree rather than headers alone, then walks it for the best text part. The body is handed
     * back exactly as that part carried it — HTML is not converted — with {@code mimeType} naming
     * what it is. Both are {@code null} when nothing in the message is readable text.
     */
    public GmailMessageBody getMessageBody(String messageId) {
        log.info("Retrieving message body for id {}", messageId);

        GmailMessageMetadata message = fullMessage(messageId);

        if (message == null) {
            throw new GmailException("Gmail returned an empty response for message %s".formatted(messageId), "");
        }

        List<GmailHeader> headers = headers(message);

        DecodedText decodedText = decodedText(message.payload());

        return new GmailMessageBody(
                message.id(),
                header(headers, SUBJECT_HEADER, NO_SUBJECT),
                header(headers, FROM_HEADER, UNKNOWN_SENDER),
                header(headers, DATE_HEADER, ""),
                decodedText == null ? null : decodedText.mimeType(),
                decodedText == null ? null : decodedText.body());
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

    private GmailMessageMetadata fullMessage(String messageId) {
        String[] basePathSegments = {GMAIL_PATH, VERSION_PATH, USERS_PATH, ME, MESSAGES_PATH, messageId};

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .queryParam(FORMAT_PARAM, FULL_FORMAT)
                        .build())
                .exchangeToMono(response -> {
                    // A 404 means the id is wrong or the message belongs to someone else. Callers
                    // answer that with a sentence, so it gets its own type rather than a GmailException.
                    if (response.statusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                        return response.releaseBody()
                                .then(Mono.error(new GmailMessageNotFoundException(
                                        "No Gmail message found with id '%s'".formatted(messageId))));
                    }

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

    /** A text part that actually decoded: the MIME type it came from, and its content. */
    private record DecodedText(String mimeType, String body) {
    }

    /**
     * The best readable text in the tree: the first {@code text/plain}, else the first
     * {@code text/html}. Each pass is depth-first, so a {@code text/plain} nested inside a
     * {@code multipart/alternative} still beats a {@code text/html} sitting higher up.
     * <p>
     * Decoding happens as part of choosing rather than after it. A part whose data is corrupt is not
     * a readable candidate, so it falls through to the next MIME type instead of hiding an otherwise
     * perfectly good sibling behind a "no readable text" answer.
     */
    private DecodedText decodedText(GmailMessagePart payload) {
        for (String candidateMimeType : TEXT_MIME_TYPES) {
            GmailMessagePart candidatePart = findPart(payload, candidateMimeType);

            if (candidatePart == null) {
                continue;
            }

            String decodedBody = decodeBase64Url(candidatePart.body().data());

            if (decodedBody != null) {
                return new DecodedText(candidatePart.mimeType(), decodedBody);
            }
        }

        return null;
    }

    private GmailMessagePart findPart(GmailMessagePart part, String mimeType) {
        if (part == null) {
            return null;
        }

        if (mimeType.equalsIgnoreCase(part.mimeType()) && carriesData(part)) {
            return part;
        }

        if (part.parts() == null) {
            return null;
        }

        for (GmailMessagePart childPart : part.parts()) {
            GmailMessagePart match = findPart(childPart, mimeType);

            if (match != null) {
                return match;
            }
        }

        return null;
    }

    /** Attachment parts name an {@code attachmentId} instead of inline {@code data}. */
    private boolean carriesData(GmailMessagePart part) {
        return part.body() != null && part.body().data() != null && !part.body().data().isBlank();
    }

    /**
     * Gmail encodes {@code body.data} with the URL-safe alphabet — {@code -} and {@code _} in place
     * of {@code +} and {@code /} — so the standard decoder rejects real payloads outright.
     * <p>
     * The bytes are read as UTF-8 unconditionally. A part declaring a different charset in its own
     * {@code Content-Type} header (for example {@code text/plain; charset=ISO-8859-1}) decodes to
     * mojibake; reading the declared charset is not implemented.
     */
    private String decodeBase64Url(String data) {
        try {
            return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException illegalArgumentException) {
            log.warn("Gmail body data is not valid base64url - {}", illegalArgumentException.getMessage());

            return null;
        }
    }
}
