package com.solesonic.a2a.service;

import com.solesonic.a2a.config.ServerCallContextFactory;
import io.a2a.server.ServerCallContext;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.adapter.JdkFlowAdapter;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public class StreamingA2AService {

    private static final Logger log = LoggerFactory.getLogger(StreamingA2AService.class);

    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    public static final String MESSAGE_STREAM = "message/stream";
    public static final String TASKS_RESUBSCRIBE = "tasks/resubscribe";

    private final RequestHandler requestHandler;
    private final JsonMapper jsonMapper;
    private final ServerCallContextFactory serverCallContextFactory;

    public StreamingA2AService(RequestHandler requestHandler, JsonMapper jsonMapper, ServerCallContextFactory serverCallContextFactory) {
        this.requestHandler = requestHandler;
        this.jsonMapper = jsonMapper;
        this.serverCallContextFactory = serverCallContextFactory;
    }

    public SseEmitter stream(SendStreamingMessageRequest request) {
        ServerCallContext context = serverCallContextFactory.create();
        return executeStreamRpc(request.getId(), MESSAGE_STREAM,
                () -> requestHandler.onMessageSendStream(request.getParams(), context));
    }

    public SseEmitter resubscribe(TaskResubscriptionRequest taskResubscriptionRequest) {
        ServerCallContext serverCallContext = serverCallContextFactory.create();

        return executeStreamRpc(taskResubscriptionRequest.getId(), TASKS_RESUBSCRIBE,
                () -> requestHandler.onResubscribeToTask(taskResubscriptionRequest.getParams(), serverCallContext));
    }

    private SseEmitter executeStreamRpc(
            Object id,
            String methodName,
            Callable<Flow.Publisher<StreamingEventKind>> publisherCallable) {
        try {
            return streamFromPublisher(id, publisherCallable.call());
        } catch (JSONRPCError jsonRpcError) {
            return sseError(id, jsonRpcError);
        } catch (IllegalArgumentException invalidParams) {
            return sseError(id, new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling {}: id={}", methodName, id, unexpected);
            return sseError(id, new JSONRPCError(INTERNAL_ERROR, "Internal error", null));
        }
    }

    private SseEmitter streamFromPublisher(Object id, Flow.Publisher<StreamingEventKind> eventKindPublisher) {
        SseEmitter sseEmitter = new SseEmitter(300_000L);

        // EventConsumer's ZeroPublisher runs its polling loop synchronously on the subscribing
        // thread. Subscribing on a background thread lets Spring commit the SSE response headers
        // immediately, so each emitter.send() flushes to the client as events arrive rather than
        // buffering until the workflow completes.
        CompletableFuture.runAsync(() ->
            JdkFlowAdapter.flowPublisherToFlux(eventKindPublisher)
                    .subscribe(
                            streamingEventKind -> {
                            try {
                                SendStreamingMessageResponse sendStreamingMessageResponse = new SendStreamingMessageResponse(id, streamingEventKind);

                                String streamingResponse = jsonMapper.writeValueAsString(sendStreamingMessageResponse);

                                sseEmitter.send(SseEmitter.event()
                                        .data(streamingResponse, MediaType.APPLICATION_JSON));
                            } catch (Exception sendError) {
                                sseEmitter.completeWithError(sendError);
                            }
                        },
                        sseEmitter::completeWithError,
                        sseEmitter::complete
                    )
        );

        return sseEmitter;
    }

    public SseEmitter sseError(Object id, JSONRPCError jsonrpcError) {
        SseEmitter sseEmitter = new SseEmitter(0L);

        try {
            SendStreamingMessageResponse sendStreamingMessageResponse = new SendStreamingMessageResponse(id, jsonrpcError);

            String messageResponse = jsonMapper.writeValueAsString(sendStreamingMessageResponse);

            sseEmitter.send(SseEmitter.event()
                    .data(messageResponse, MediaType.APPLICATION_JSON));

        } catch (Exception serializationError) {
            sseEmitter.completeWithError(serializationError);
        }

        sseEmitter.complete();
        return sseEmitter;
    }
}
