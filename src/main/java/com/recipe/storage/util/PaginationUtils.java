package com.recipe.storage.util;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Utility functions for cursor-based pagination in Firestore.
 */
public final class PaginationUtils {

  private PaginationUtils() {
    // Utility class
  }

  /**
   * Partitions a list into batches of at most {@code batchSize} elements.
   *
   * @param <T>       the element type
   * @param list      the list to partition
   * @param batchSize maximum elements per batch
   * @return list of partition sublists
   */
  public static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
    List<List<T>> partitions = new ArrayList<>();
    for (int i = 0; i < list.size(); i += batchSize) {
      partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
    }
    return partitions;
  }

  /**
   * Decodes an opaque page token into a Firestore {@link Timestamp} cursor.
   *
   * @param pageToken the opaque cursor token from a previous paged response
   * @return the decoded Firestore Timestamp to pass to {@code startAfter()}
   * @throws ResponseStatusException 400 Bad Request if the token is malformed
   */
  public static Timestamp decodePageToken(String pageToken) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(pageToken);
      String cursor = new String(decoded, StandardCharsets.UTF_8);
      String[] parts = cursor.split(",", 2);
      if (parts.length != 2) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page token");
      }
      return Timestamp.ofTimeSecondsAndNanos(
          Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page token");
    }
  }

  /**
   * Encodes the given timestamp field of the last document in a query snapshot
   * into an opaque page token for cursor-based pagination.
   *
   * @param querySnapshot the Firestore query result snapshot
   * @param fieldName     the name of the timestamp field to use as cursor
   * @return a URL-safe base64 cursor token, or {@code null} if no next page exists
   */
  public static String encodeNextPageTokenFromField(QuerySnapshot querySnapshot, String fieldName) {
    if (querySnapshot == null || querySnapshot.isEmpty()) {
      return null;
    }
    List<? extends DocumentSnapshot> docs = querySnapshot.getDocuments();
    DocumentSnapshot lastDoc = docs.get(docs.size() - 1);
    Timestamp lastTimestamp = lastDoc.getTimestamp(fieldName);
    if (lastTimestamp == null) {
      return null;
    }
    String cursor = lastTimestamp.getSeconds() + "," + lastTimestamp.getNanos();
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(cursor.getBytes(StandardCharsets.UTF_8));
  }
}
