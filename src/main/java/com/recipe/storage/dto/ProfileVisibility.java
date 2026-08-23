package com.recipe.storage.dto;

import java.util.Locale;

/**
 * Controls which profile fields may be returned by the public profile endpoint.
 */
public enum ProfileVisibility {
  PUBLIC,
  PRIVATE;

  /**
   * Converts a persisted visibility value to the canonical enum.
   *
   * <p>Profiles created before visibility was introduced remain public for backwards
   * compatibility. Unrecognised persisted values are treated as private to avoid accidental
   * disclosure.
   *
   * @param value persisted Firestore value
   * @return the resolved profile visibility
   */
  public static ProfileVisibility fromFirestoreValue(String value) {
    if (value == null || value.isBlank()) {
      return PUBLIC;
    }

    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return PRIVATE;
    }
  }
}
