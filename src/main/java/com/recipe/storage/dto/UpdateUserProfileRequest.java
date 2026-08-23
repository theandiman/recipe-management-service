package com.recipe.storage.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Editable fields of a canonical user profile.
 *
 * <p>UIDs, timestamps, and follow counts are intentionally omitted because the service owns
 * those values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Editable canonical profile fields")
public class UpdateUserProfileRequest {

  @NotBlank(message = "displayName must not be blank")
  @Size(max = 100, message = "displayName must be at most 100 characters")
  @Schema(description = "Public display name", example = "Chef Andy", maxLength = 100)
  private String displayName;

  @Size(max = 500, message = "bio must be at most 500 characters")
  @Schema(description = "Optional profile biography", example = "Home cook and pasta enthusiast",
      maxLength = 500, nullable = true)
  private String bio;

  @Size(max = 2048, message = "avatarUrl must be at most 2048 characters")
  @Pattern(regexp = "^$|https?://[^\\s]+$",
      message = "avatarUrl must be an absolute HTTP(S) URL")
  @Schema(description = "Optional absolute HTTP(S) avatar URL. Supply an empty string to clear it",
      example = "https://example.com/avatar.png", maxLength = 2048, nullable = true)
  private String avatarUrl;

  @NotNull(message = "visibility is required")
  @Schema(description = "Controls which profile fields are public", example = "PUBLIC")
  private ProfileVisibility visibility;

  @JsonIgnore
  private final Set<String> unsupportedFields = new LinkedHashSet<>();

  /**
   * Records unrecognised JSON properties so validation rejects attempted writes to service-owned
   * profile fields.
   *
   * @param field unrecognised JSON property name
   * @param ignoredValue unrecognised JSON property value
   */
  @JsonAnySetter
  public void captureUnsupportedField(String field, Object ignoredValue) {
    unsupportedFields.add(field);
  }

  /**
   * Verifies that the request contains only editable profile fields.
   *
   * @return whether all supplied properties are supported
   */
  @AssertTrue(message = "contains unsupported profile fields")
  @JsonIgnore
  public boolean isLimitedToEditableFields() {
    return unsupportedFields.isEmpty();
  }
}
