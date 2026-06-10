package com.solesonic.agent.sports.node;

import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;

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

    static SynthesisOutputEmitter fromTaskUpdater(TaskUpdater taskUpdater) {
        return new SynthesisOutputEmitter() {
            @Override
            public void emitChunk(String text, String artifactId, Boolean append, boolean lastChunk) {
                taskUpdater.addArtifact(List.of(new TextPart(text)), artifactId, null, null, append, lastChunk);
            }

            @Override
            public void emitProgress(String message) {
                List<Part<?>> messageParts = List.of(new TextPart(message));
                Message statusMessage = taskUpdater.newAgentMessage(messageParts, null);
                taskUpdater.updateStatus(TaskState.WORKING, statusMessage);
            }
        };
    }
}
