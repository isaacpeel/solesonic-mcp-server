package com.solesonic.repository.comfyui;

import com.solesonic.model.comfyui.ComfyWorkflow;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComfyWorkflowRepository extends JpaRepository<ComfyWorkflow, UUID> {

    /**
     * Fetches the reference variants and intents in the same query: the registry reads them once at
     * startup, outside any transaction, where a lazy collection could not be loaded.
     */
    @EntityGraph(attributePaths = {"referenceVariants", "intents"})
    List<ComfyWorkflow> findAllByEnabledTrueOrderByToolNameAsc();
}
