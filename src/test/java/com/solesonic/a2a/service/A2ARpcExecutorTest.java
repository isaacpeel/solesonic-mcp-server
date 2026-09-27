package com.solesonic.a2a.service;

import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class A2ARpcExecutorTest {

    private final A2ARpcExecutor executor = new A2ARpcExecutor();

    @Test
    void execute_success_returnsSuccessBody() {
        ResponseEntity<String> response = executor.execute("id-1", "TestMethod",
                () -> "ok", _ -> "unused");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo("ok");
    }

    @Test
    void execute_a2aError_mapsThroughErrorBody() {
        ResponseEntity<String> response = executor.execute("id-1", "TestMethod",
                () -> { throw new InvalidRequestError("bad request"); },
                error -> "mapped: " + error.getMessage());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("mapped: bad request");
    }

    @Test
    void execute_illegalArgument_mapsToInvalidParams() {
        ResponseEntity<String> response = executor.execute("id-1", "TestMethod",
                () -> { throw new IllegalArgumentException("bad param"); },
                A2AError::getMessage);

        assertThat(response.getBody()).isEqualTo("Invalid params: bad param");
    }

    @Test
    void execute_unexpectedException_mapsToInternalError() {
        ResponseEntity<String> response = executor.execute("id-1", "TestMethod",
                () -> { throw new RuntimeException("boom"); },
                A2AError::getMessage);

        assertThat(response.getBody()).isEqualTo("Internal error");
    }
}