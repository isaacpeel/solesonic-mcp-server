package com.solesonic.model.comfyui;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A stored ComfyUI workflow. Each enabled row becomes one MCP tool at startup, so the row is the
 * unit of choice the client sees: picking a tool is picking a workflow.
 *
 * <p>Generation parameters other than the prompt and the substitution tokens live inside
 * {@code workflowJson} rather than in columns — a row is a preset, and two models at two step counts
 * are two rows rather than one row with knobs.
 *
 * <p>{@code created_at} and {@code updated_at} exist on the table but are deliberately unmapped:
 * they are maintained by the database and nothing in the application reads them.
 */
@Entity
@Table(name = "comfy_workflow")
public class ComfyWorkflow {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "tool_name", nullable = false, unique = true)
    private String toolName;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "workflow_json", nullable = false)
    private String workflowJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWorkflowJson() {
        return workflowJson;
    }

    public void setWorkflowJson(String workflowJson) {
        this.workflowJson = workflowJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
