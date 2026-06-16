package com.solesonic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class SolesonicMcpServer {

    static void main(String[] args) {
        SpringApplication.run(SolesonicMcpServer.class, args);
    }

}
