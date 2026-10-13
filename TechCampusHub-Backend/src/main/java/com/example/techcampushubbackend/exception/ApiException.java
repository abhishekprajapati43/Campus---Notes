package com.example.techcampushubbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception carrying the HTTP status the client should receive.
 * Throw it from services; {@link GlobalExceptionHandler} renders it as JSON.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
