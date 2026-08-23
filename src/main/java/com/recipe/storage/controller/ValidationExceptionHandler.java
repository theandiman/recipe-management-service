package com.recipe.storage.controller;

import com.recipe.storage.dto.ValidationErrorResponse;
import com.recipe.storage.dto.ValidationViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler that maps bean-validation constraint violations
 * on controller method parameters to HTTP 400 Bad Request.
 */
@RestControllerAdvice
public class ValidationExceptionHandler {

  /**
   * Handle invalid request-body fields with a documented validation response.
   *
   * @param ex request-body validation failure
   * @return structured field validation errors
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ValidationErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    List<ValidationViolation> violations = ex.getBindingResult().getAllErrors().stream()
        .map(error -> ValidationViolation.builder()
            .field(error instanceof FieldError fieldError ? fieldError.getField() : "request")
            .message(error.getDefaultMessage())
            .build())
        .toList();
    return ResponseEntity.badRequest().body(ValidationErrorResponse.builder()
        .status(HttpStatus.BAD_REQUEST.value())
        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
        .message("Invalid request body")
        .violations(violations)
        .build());
  }

  /**
   * Handle {@code ConstraintViolationException} thrown by {@code @Validated} +
   * {@code @Min}/{@code @Max} on {@code @RequestParam} or {@code @PathVariable}
   * and map it to 400 Bad Request.
   *
   * @param ex the constraint violation exception
   * @return a structured error response with status and message
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> handleConstraintViolation(
      ConstraintViolationException ex) {
    Map<String, Object> body = Map.of(
        "status", HttpStatus.BAD_REQUEST.value(),
        "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
        "message", "Invalid request parameters");
    return ResponseEntity.badRequest().body(body);
  }
}
