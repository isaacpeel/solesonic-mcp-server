package com.solesonic.agent.checkpoint;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.bsc.langgraph4j.serializer.PlainTextStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.io.IOException;
import java.util.Map;

/**
 * Serializes graph state for checkpoint storage with the application's Jackson 3 mapper, recording
 * each value's concrete type so domain records ({@code Board}, {@code JiraIssueCreatePayload},
 * {@code CallerIdentity}, …) come back as themselves rather than as generic maps.
 * <p>
 * Type ids are only honoured for an allow-list — this project's own types plus JDK collections,
 * numbers and time types — so a tampered checkpoint cannot name an arbitrary class to instantiate.
 */
public class JsonMapperStateSerializer<State extends AgentState> extends PlainTextStateSerializer<State> {

    private static final String CONTENT_TYPE = "application/json";
    private static final TypeReference<Map<String, Object>> STATE_DATA_TYPE = new TypeReference<>() {};

    private final JsonMapper typedJsonMapper;

    public JsonMapperStateSerializer(AgentStateFactory<State> stateFactory, JsonMapper jsonMapper) {
        super(stateFactory);

        PolymorphicTypeValidator allowedStateTypes = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.solesonic.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType(Number.class)
                .build();

        this.typedJsonMapper = jsonMapper.rebuild()
                .activateDefaultTyping(allowedStateTypes, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
                .build();
    }

    @Override
    public String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    public String writeDataAsString(Map<String, Object> data) throws IOException {
        try {
            return typedJsonMapper.writerFor(STATE_DATA_TYPE).writeValueAsString(data);
        } catch (JacksonException jacksonException) {
            throw new IOException("Failed to serialize graph state for checkpointing", jacksonException);
        }
    }

    @Override
    public Map<String, Object> readDataFromString(String text) throws IOException {
        try {
            return typedJsonMapper.readValue(text, STATE_DATA_TYPE);
        } catch (JacksonException jacksonException) {
            throw new IOException("Failed to deserialize checkpointed graph state", jacksonException);
        }
    }
}
