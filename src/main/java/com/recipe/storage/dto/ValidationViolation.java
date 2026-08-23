package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single invalid request field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A validation failure for one request field")
public class ValidationViolation {

  @Schema(description = "Invalid field name", example = "displayName")
  private String field;

  @Schema(description = "Reason the value was rejected",
      example = "displayName must not be blank")
  private String message;
}
