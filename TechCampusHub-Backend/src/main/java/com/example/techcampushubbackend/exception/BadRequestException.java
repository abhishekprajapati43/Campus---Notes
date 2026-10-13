package com.example.techcampushubbackend.exception;

import org.springframework.http.HttpStatus;

/** 400 - the request is semantically wrong (bad credentials, invalid state...). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
