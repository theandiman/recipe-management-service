package com.recipe.storage.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating user profile settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

  @Size(max = 50, message = "Display name cannot exceed 50 characters")
  private String displayName;

  @Size(max = 500, message = "Bio cannot exceed 500 characters")
  private String bio;

  @Size(max = 1000, message = "Avatar URL cannot exceed 1000 characters")
  private String avatarUrl;

  @Pattern(regexp = "^(PUBLIC|PRIVATE)$", message = "Visibility must be PUBLIC or PRIVATE")
  private String visibility;
}
