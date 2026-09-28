package com.example.esgaward.exception;

/** OpenFGA could not be reached or returned an error; the request is denied (fail closed). */
public class AuthorizationUnavailableException extends RuntimeException {

    public AuthorizationUnavailableException(Throwable cause) {
        super("Authorization service unavailable", cause);
    }
}
