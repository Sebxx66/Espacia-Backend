package com.bibliospace.backend.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// Error de negocio con un mensaje pensado para mostrarse tal cual en el frontend
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    // Código opcional para que el frontend reaccione a un caso concreto (por ejemplo, EMAIL_NO_VERIFICADO)
    private final String code;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }
}
