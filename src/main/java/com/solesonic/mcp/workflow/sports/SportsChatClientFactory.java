package com.solesonic.mcp.workflow.sports;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class SportsChatClientFactory {

    private static final String MODEL_SMALL = "qwen3:4b";
    private static final String MODEL_MEDIUM = "qwen3:8b";
    private static final String MODEL_LARGE = "qwen3:14b";

    private static final int NUM_THREADS = 12;
    private static final int ALL_GPU_LAYERS = 999;
    private static final int GPU_0 = 0;
    private static final int GPU_1 = 1;

    private final OllamaApi ollamaApi;
    private final ConcurrentHashMap<SportsChatProfile, ChatClient> cache = new ConcurrentHashMap<>();

    public SportsChatClientFactory(OllamaApi ollamaApi) {
        this.ollamaApi = ollamaApi;
    }

    public ChatClient forProfile(SportsChatProfile profile) {
        return cache.computeIfAbsent(profile, this::buildChatClient);
    }

    private ChatClient buildChatClient(SportsChatProfile profile) {
        OllamaChatOptions options = buildOptions(profile);

        ModelManagementOptions modelManagementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.WHEN_MISSING)
                .build();

        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                .defaultOptions(options)
                .ollamaApi(ollamaApi)
                .modelManagementOptions(modelManagementOptions)
                .build();

        return ChatClient.builder(ollamaChatModel).build();
    }

    private OllamaChatOptions buildOptions(SportsChatProfile profile) {
        return switch (profile) {
            case INTENT_PARSE -> OllamaChatOptions.builder()
                    .model(MODEL_SMALL)
                    .numThread(NUM_THREADS)
                    .numGPU(ALL_GPU_LAYERS)
                    .mainGPU(GPU_0)
                    .numCtx(8_192)
                    .numBatch(512)
                    .temperature(0.0)
                    .topK(1)
                    .disableThinking()
                    .keepAlive("10m")
                    .build();

            case ROSTER_VALIDATION -> OllamaChatOptions.builder()
                    .model(MODEL_MEDIUM)
                    .numThread(NUM_THREADS)
                    .numGPU(ALL_GPU_LAYERS)
                    .mainGPU(GPU_1)
                    .numCtx(32_768)
                    .numBatch(512)
                    .temperature(0.0)
                    .topK(1)
                    .repeatPenalty(1.1)
                    .disableThinking()
                    .build();

            case PLAYER_ANALYSIS -> OllamaChatOptions.builder()
                    .model(MODEL_MEDIUM)
                    .numThread(NUM_THREADS)
                    .numGPU(ALL_GPU_LAYERS)
                    .mainGPU(GPU_1)
                    .numCtx(65_536)
                    .numBatch(1_024)
                    .temperature(0.3)
                    .topP(0.9)
                    .repeatPenalty(1.1)
                    .repeatLastN(128)
                    .enableThinking()
                    .build();

            case SYNTHESIS -> OllamaChatOptions.builder()
                    .model(MODEL_LARGE)
                    .numThread(NUM_THREADS)
                    .numGPU(ALL_GPU_LAYERS)
                    .mainGPU(GPU_0)
                    .numCtx(131_072)
                    .numBatch(1_024)
                    .temperature(0.1)
                    .topP(0.95)
                    .repeatPenalty(1.05)
                    .enableThinking()
                    .keepAlive("30m")
                    .build();
        };
    }
}
