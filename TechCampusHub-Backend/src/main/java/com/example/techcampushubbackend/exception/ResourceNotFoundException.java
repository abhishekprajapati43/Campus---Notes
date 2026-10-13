package com.example.techcampushubbackend.exception;

import org.springframework.http.HttpStatus;

/** 404 - the requested entity does not exist. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, resource + " not found: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
