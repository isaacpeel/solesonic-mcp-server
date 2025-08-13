package com.solesonic.mcp.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class SimpleService {

    @Tool(description = "Return a simple message", name = "message_me")
    public String message(String message) {
        return "Your message: "+message;
    }
}
