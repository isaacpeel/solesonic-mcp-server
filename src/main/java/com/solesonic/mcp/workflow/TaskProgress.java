package com.solesonic.mcp.workflow;

public interface TaskProgress {
    void update(double fraction, String message);
    void done(String message);
}
