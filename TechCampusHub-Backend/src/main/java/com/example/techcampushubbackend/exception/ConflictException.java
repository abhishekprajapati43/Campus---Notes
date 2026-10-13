package com.example.techcampushubbackend.exception;

import org.springframework.http.HttpStatus;

/** 409 - the request conflicts with existing state (duplicate e-mail...). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
