package com.solesonic.agent.checkpoint;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;
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
 * <p>
 * Bound to plain {@link AgentState}: every graph shares one saver, and the saver only round-trips the
 * state's data map, so each graph's typed state is rebuilt by the graph itself, not here.
 */
@Component
public class JsonMapperStateSerializer extends PlainTextStateSerializer<AgentState> {

    private static final String CONTENT_TYPE = "application/json";
    private static final TypeReference<Map<String, Object>> STATE_DATA_TYPE = new TypeReference<>() {};

    private final JsonMapper typedJsonMapper;

    public JsonMapperStateSerializer(JsonMapper jsonMapper) {
        super(AgentState::new);

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
    public String writeDataAsString(AgentState state) throws IOException {
        try {
            return typedJsonMapper.writerFor(STATE_DATA_TYPE).writeValueAsString(state.data());
        } catch (JacksonException jacksonException) {
            throw new IOException("Failed to serialize graph state for checkpointing", jacksonException);
        }
    }

    @Override
    public AgentState readDataFromString(String text) throws IOException {
        try {
            return stateOf(typedJsonMapper.readValue(text, STATE_DATA_TYPE));
        } catch (JacksonException jacksonException) {
            throw new IOException("Failed to deserialize checkpointed graph state", jacksonException);
        }
    }
}
