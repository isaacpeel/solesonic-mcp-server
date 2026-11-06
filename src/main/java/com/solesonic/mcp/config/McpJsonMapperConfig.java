package com.solesonic.mcp.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Works around Jackson 3.2.x rejecting the MCP SDK's {@link ElicitRequest}.
 *
 * <p>The SDK declares {@code @JsonTypeInfo(include = PROPERTY, property = "mode")} on the interface and
 * also exposes a {@code mode()} accessor on its records. Jackson 3.2 treats that as a type id
 * conflicting with a bean property and fails with "Conflict between type id property 'mode' and bean
 * property with same name", so no elicitation (assignee picker, delete confirmations) could be sent.
 * {@code EXISTING_PROPERTY} is the remedy Jackson's own message suggests: the record's {@code mode()}
 * supplies the discriminator instead of Jackson writing a second one.
 *
 * <p>The transport serializes with Spring AI's {@code mcpServerJsonMapper}, not the application's
 * {@code JsonMapper}, and that mapper is immutable, so the bean is replaced by a rebuilt copy carrying
 * the mixin. Remove this once the SDK or Jackson fixes the conflict.
 */
@Configuration
public class McpJsonMapperConfig {

    static final String MCP_SERVER_JSON_MAPPER = "mcpServerJsonMapper";

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "mode")
    interface ElicitRequestTypeInfo {
    }

    @Bean
    public static BeanPostProcessor mcpServerJsonMapperPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (MCP_SERVER_JSON_MAPPER.equals(beanName) && bean instanceof JsonMapper jsonMapper) {
                    return withElicitRequestMixIn(jsonMapper);
                }

                return bean;
            }
        };
    }

    static JsonMapper withElicitRequestMixIn(JsonMapper jsonMapper) {
        return jsonMapper.rebuild()
                .addMixIn(ElicitRequest.class, ElicitRequestTypeInfo.class)
                .build();
    }
}
