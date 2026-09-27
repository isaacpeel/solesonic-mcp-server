package com.solesonic.a2a.service;

import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.function.Function;

// Shared executeRpc/jsonResponse pair, previously duplicated byte-for-byte in TaskService and PushNotificationService.
@Component
public class A2ARpcExecutor {

    private static final Logger log = LoggerFactory.getLogger(A2ARpcExecutor.class);

    public <T> ResponseEntity<T> execute(
            Object id,
            String methodName,
            Callable<T> successBody,
            Function<A2AError, T> errorBody) {
        try {
            return jsonResponse(successBody.call());
        } catch (A2AError a2aError) {
            return jsonResponse(errorBody.apply(a2aError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(errorBody.apply(
                    new InvalidParamsError("Invalid params: " + invalidParams.getMessage())));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling {}: id={}", methodName, id, unexpected);
            return jsonResponse(errorBody.apply(new InternalError("Internal error")));
        }
    }

    private static <T> ResponseEntity<T> jsonResponse(T body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}