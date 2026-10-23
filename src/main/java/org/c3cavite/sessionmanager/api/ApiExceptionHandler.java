package org.c3cavite.sessionmanager.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ErrorResponse invalidInput(IllegalArgumentException error) {
    return new ErrorResponse(error.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ErrorResponse unreadableRequest() {
    return new ErrorResponse(
        "Request must contain valid JSON with the documented field types and enum values");
  }

  record ErrorResponse(String message) {}
}
