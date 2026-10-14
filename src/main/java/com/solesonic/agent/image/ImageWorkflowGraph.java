package com.solesonic.agent.image;

import com.solesonic.agent.image.node.ClassifyImageIntentNode;
import com.solesonic.agent.image.node.ResolveImageWorkflowNode;
import com.solesonic.agent.image.node.SelectImageWorkflowNode;
import com.solesonic.agent.image.node.WriteImagePromptNode;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

/**
 * Routes an image request to one stored ComfyUI workflow and writes its prompt:
 * classify the intent, select a workflow for it, make that choice safe to run, then write the
 * prompt for the chosen workflow. The graph only prepares: generation runs in the tool after the
 * graph ends, so the reference images never enter checkpointed state.
 */
@Configuration
public class ImageWorkflowGraph {

    public static final String GRAPH_NAME = "image-workflow";

    public static final String CLASSIFY_INTENT = "classifyImageIntent";
    public static final String SELECT_WORKFLOW = "selectImageWorkflow";
    public static final String RESOLVE_WORKFLOW = "resolveImageWorkflow";
    public static final String WRITE_PROMPT = "writeImagePrompt";

    // Not named imageWorkflowGraph: that is this configuration class's own bean name.
    @Bean
    public CompiledGraph<ImageWorkflowState> imageRouterGraph(
            ClassifyImageIntentNode classifyImageIntentNode,
            SelectImageWorkflowNode selectImageWorkflowNode,
            ResolveImageWorkflowNode resolveImageWorkflowNode,
            WriteImagePromptNode writeImagePromptNode,
            CompileConfig graphCompileConfig
    ) throws GraphStateException {

        return new StateGraph<>(ImageWorkflowState::new)
                .addNode(CLASSIFY_INTENT, classifyImageIntentNode)
                .addNode(SELECT_WORKFLOW, selectImageWorkflowNode)
                .addNode(RESOLVE_WORKFLOW, resolveImageWorkflowNode)
                .addNode(WRITE_PROMPT, writeImagePromptNode)
                .addEdge(START, CLASSIFY_INTENT)
                .addEdge(CLASSIFY_INTENT, SELECT_WORKFLOW)
                .addEdge(SELECT_WORKFLOW, RESOLVE_WORKFLOW)
                .addEdge(RESOLVE_WORKFLOW, WRITE_PROMPT)
                .addEdge(WRITE_PROMPT, END)
                .compile(graphCompileConfig);
    }
}
