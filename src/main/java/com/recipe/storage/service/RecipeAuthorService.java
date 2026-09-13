package com.recipe.storage.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import java.util.concurrent.ExecutionException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for resolving author metadata (display name and avatar URL)
 * from Firestore user profiles with fallback to Firebase Authentication.
 */
@Slf4j
@Service
@NoArgsConstructor
@AllArgsConstructor
public class RecipeAuthorService {

  @Autowired(required = false)
  private Firestore firestore;

  @Autowired(required = false)
  private FirebaseAuth firebaseAuth;

  @Value("${firestore.collection.users:users}")
  private String usersCollection = "users";

  /**
   * Helper record to hold resolved author metadata (display name and avatar URL).
   */
  public record AuthorInfo(String displayName, String avatarUrl) {}

  /**
   * Helper to resolve author metadata (display name and avatar URL) from Firestore profile
   * or Firebase Auth.
   *
   * @param userId The user ID
   * @return The resolved author information
   */
  public AuthorInfo resolveAuthorInfo(String userId) {
    if (userId == null) {
      return new AuthorInfo(null, null);
    }
    String displayName = null;
    String avatarUrl = null;

    if (firestore != null && usersCollection != null) {
      try {
        var collection = firestore.collection(usersCollection);
        if (collection != null) {
          var docRef = collection.document(userId);
          if (docRef != null) {
            DocumentSnapshot userDoc = docRef.get().get();
            if (userDoc != null && userDoc.exists()) {
              String docDisplayName = userDoc.getString("displayName");
              if (docDisplayName != null && !docDisplayName.isBlank()) {
                displayName = docDisplayName;
              }
              String docAvatarUrl = userDoc.getString("avatarUrl");
              if (docAvatarUrl != null && !docAvatarUrl.isBlank()) {
                avatarUrl = docAvatarUrl;
              }
            }
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Interrupted while resolving profile for user {}", userId);
      } catch (ExecutionException e) {
        log.warn("Failed to resolve profile from Firestore for user {}: {}",
            userId, e.getMessage());
      } catch (Exception e) {
        log.warn("Unexpected error resolving profile from Firestore for user {}: {}",
            userId, e.getMessage());
      }
    }

    if (firebaseAuth != null && (displayName == null || avatarUrl == null)) {
      try {
        UserRecord userRecord = firebaseAuth.getUser(userId);
        if (userRecord != null) {
          if (displayName == null && userRecord.getDisplayName() != null
              && !userRecord.getDisplayName().isBlank()) {
            displayName = userRecord.getDisplayName();
          }
          if (avatarUrl == null && userRecord.getPhotoUrl() != null
              && !userRecord.getPhotoUrl().isBlank()) {
            avatarUrl = userRecord.getPhotoUrl();
          }
        }
      } catch (FirebaseAuthException e) {
        log.warn("Failed to resolve profile from Firebase Auth for user {}: {}",
            userId, e.getMessage());
      }
    }

    return new AuthorInfo(displayName, avatarUrl);
  }

  /**
   * Resolve a user's display name from Firestore profile or Firebase Auth,
   * returning null on failure.
   *
   * @param userId The Firebase user ID
   * @return The display name, or null if lookup fails or auth is unavailable
   */
  public String resolveDisplayName(String userId) {
    return resolveAuthorInfo(userId).displayName();
  }

  /**
   * Helper to resolve the avatar URL for a user from Firestore profile or Firebase Auth.
   *
   * @param userId The Firebase user ID
   * @return The avatar URL, or null if not set or lookup fails
   */
  public String resolveAvatarUrl(String userId) {
    return resolveAuthorInfo(userId).avatarUrl();
  }
}
