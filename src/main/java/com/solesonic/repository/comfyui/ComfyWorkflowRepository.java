package com.solesonic.repository.comfyui;

import com.solesonic.model.comfyui.ComfyWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComfyWorkflowRepository extends JpaRepository<ComfyWorkflow, UUID> {

    List<ComfyWorkflow> findAllByEnabledTrueOrderByToolNameAsc();
}
