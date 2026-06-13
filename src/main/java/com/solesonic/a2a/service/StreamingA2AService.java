package com.solesonic.a2a.service;

import com.solesonic.a2a.config.AgentRequestHandlerRegistry;
import com.solesonic.a2a.config.ServerCallContextFactory;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SubscribeToTaskRequest;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.adapter.JdkFlowAdapter;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

@Service
public class StreamingA2AService {

    private static final Logger log = LoggerFactory.getLogger(StreamingA2AService.class);

    public static final String MESSAGE_STREAM = "SendStreamingMessage";
    public static final String TASKS_RESUBSCRIBE = "SubscribeToTask";

    private final AgentRequestHandlerRegistry agentRequestHandlerRegistry;
    private final JsonMapper jsonMapper;
    private final ServerCallContextFactory serverCallContextFactory;

    public StreamingA2AService(AgentRequestHandlerRegistry agentRequestHandlerRegistry,
                               JsonMapper jsonMapper,
                               ServerCallContextFactory serverCallContextFactory) {
        this.agentRequestHandlerRegistry = agentRequestHandlerRegistry;
        this.jsonMapper = jsonMapper;
        this.serverCallContextFactory = serverCallContextFactory;
    }

    public SseEmitter stream(String agentId, SendStreamingMessageRequest sendStreamingMessageRequest) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext serverCallContext = serverCallContextFactory.create();

        return executeStreamRpc(sendStreamingMessageRequest.getId(), MESSAGE_STREAM,
                () -> requestHandler.onMessageSendStream(sendStreamingMessageRequest.getParams(), serverCallContext));
    }

    public SseEmitter resubscribe(String agentId, SubscribeToTaskRequest subscribeToTaskRequest) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext serverCallContext = serverCallContextFactory.create();

        return executeStreamRpc(subscribeToTaskRequest.getId(), TASKS_RESUBSCRIBE,
                () -> requestHandler.onSubscribeToTask(subscribeToTaskRequest.getParams(), serverCallContext));
    }

    private SseEmitter executeStreamRpc(
            Object id,
            String methodName,
            Callable<Flow.Publisher<StreamingEventKind>> publisherCallable) {
        try {
            return streamFromPublisher(id, publisherCallable.call());
        } catch (A2AError a2aError) {
            return sseError(id, a2aError);
        } catch (IllegalArgumentException invalidParams) {
            return sseError(id, new InvalidParamsError("Invalid params: " + invalidParams.getMessage()));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling {}: id={}", methodName, id, unexpected);
            return sseError(id, new InternalError("Internal error"));
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

    public SseEmitter sseError(Object id, A2AError a2aError) {
        SseEmitter sseEmitter = new SseEmitter(0L);

        try {
            SendStreamingMessageResponse sendStreamingMessageResponse = new SendStreamingMessageResponse(id, a2aError);

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
