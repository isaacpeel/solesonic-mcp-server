package com.solesonic.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class SolesonicMcpServerApplication {

    static void main(String[] args) {
        SpringApplication.run(SolesonicMcpServerApplication.class, args);
    }

}
