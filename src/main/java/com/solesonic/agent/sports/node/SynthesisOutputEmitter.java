package com.solesonic.agent.sports.node;

import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;

import java.util.List;

/**
 * Abstracts synthesis token streaming and progress reporting away from TaskUpdater,
 * making nodes that emit output testable without an A2A request context.
 */
public interface SynthesisOutputEmitter {

    String CONFIG_KEY = "synthesisOutputEmitter";

    void emitChunk(String text, String artifactId, Boolean append, boolean lastChunk);

    void emitProgress(String message);

    static SynthesisOutputEmitter noOp() {
        return new SynthesisOutputEmitter() {
            @Override
            public void emitChunk(String text, String artifactId, Boolean append, boolean lastChunk) {
            }

            @Override
            public void emitProgress(String message) {
            }
        };
    }

    static SynthesisOutputEmitter fromTaskUpdater(AgentEmitter agentEmitter) {
        return new SynthesisOutputEmitter() {
            @Override
            public void emitChunk(String text, String artifactId, Boolean append, boolean lastChunk) {
                agentEmitter.addArtifact(List.of(new TextPart(text)), artifactId, null, null, append, lastChunk);
            }

            @Override
            public void emitProgress(String message) {
                List<Part<?>> messageParts = List.of(new TextPart(message));
                Message statusMessage = agentEmitter.newAgentMessage(messageParts, null);
                agentEmitter.updateStatus(TaskState.TASK_STATE_WORKING, statusMessage);
            }
        };
    }
}
