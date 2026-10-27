package com.pydlove.workcove.shared.interfaces.rest;

import com.pydlove.workcove.shared.domain.AuthenticationException;
import com.pydlove.workcove.shared.domain.DomainException;
import com.pydlove.workcove.shared.interfaces.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> authentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("UNAUTHORIZED", exception.getMessage()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_REQUEST", exception.getMessage()));
    }
}
