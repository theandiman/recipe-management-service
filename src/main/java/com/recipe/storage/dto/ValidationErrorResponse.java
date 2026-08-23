package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Documented validation response returned for malformed API requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Validation error response")
public class ValidationErrorResponse {

  @Schema(description = "HTTP status code", example = "400")
  private int status;

  @Schema(description = "HTTP status text", example = "Bad Request")
  private String error;

  @Schema(description = "Summary of the validation failure", example = "Invalid request body")
  private String message;

  @Schema(description = "Individual invalid request fields")
  private List<ValidationViolation> violations;
}
