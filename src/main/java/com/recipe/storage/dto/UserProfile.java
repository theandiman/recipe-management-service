package com.recipe.storage.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Canonical profile document stored at {@code users/{uid}}.
 *
 * <p>The Firebase UID is represented by the Firestore document ID. Follow counts are derived
 * from follow relationships and are never accepted from profile update requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

  private String uid;
  private String displayName;
  private String bio;
  private String avatarUrl;
  private ProfileVisibility visibility;
  private Instant createdAt;
  private Instant updatedAt;
  private long followerCount;
  private long followingCount;
}
