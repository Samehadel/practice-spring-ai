package com.practice.spring_ai.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Indicates that a generated answer cannot be returned as a supported response. */
@ResponseStatus(value = HttpStatus.BAD_GATEWAY, reason = "The generated answer could not be validated against the retrieved documents.")
public class InvalidLlmResponseException extends RuntimeException {

    public InvalidLlmResponseException() {
        super("The generated answer could not be validated against the retrieved documents.");
    }
}
