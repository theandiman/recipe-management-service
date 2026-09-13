package com.recipe.storage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PaginationUtilsTest {

  @Test
  void partitionList_PartitionsCorrectly() {
    List<Integer> list = List.of(1, 2, 3, 4, 5);
    List<List<Integer>> partitions = PaginationUtils.partitionList(list, 2);

    assertEquals(3, partitions.size());
    assertEquals(List.of(1, 2), partitions.get(0));
    assertEquals(List.of(3, 4), partitions.get(1));
    assertEquals(List.of(5), partitions.get(2));
  }

  @Test
  void decodePageToken_Success() {
    String token = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("1700000000,500".getBytes(StandardCharsets.UTF_8));

    Timestamp timestamp = PaginationUtils.decodePageToken(token);
    assertNotNull(timestamp);
    assertEquals(1700000000L, timestamp.getSeconds());
    assertEquals(500, timestamp.getNanos());
  }

  @Test
  void decodePageToken_MalformedToken_ThrowsBadRequest() {
    String badToken = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("not-a-timestamp".getBytes(StandardCharsets.UTF_8));

    assertThrows(ResponseStatusException.class, () -> PaginationUtils.decodePageToken(badToken));
  }

  @Test
  void encodeNextPageTokenFromField_EmptySnapshot_ReturnsNull() {
    QuerySnapshot snapshot = mock(QuerySnapshot.class);
    when(snapshot.isEmpty()).thenReturn(true);

    assertNull(PaginationUtils.encodeNextPageTokenFromField(snapshot, "createdAt"));
  }

  @Test
  void encodeNextPageTokenFromField_ValidSnapshot_ReturnsEncodedToken() {
    QuerySnapshot snapshot = mock(QuerySnapshot.class);
    DocumentSnapshot doc = mock(DocumentSnapshot.class);
    Timestamp timestamp = Timestamp.ofTimeSecondsAndNanos(1700000000L, 500);

    when(snapshot.isEmpty()).thenReturn(false);
    org.mockito.Mockito.doReturn(Collections.singletonList(doc)).when(snapshot).getDocuments();
    when(doc.getTimestamp("createdAt")).thenReturn(timestamp);

    String token = PaginationUtils.encodeNextPageTokenFromField(snapshot, "createdAt");
    assertNotNull(token);

    Timestamp decoded = PaginationUtils.decodePageToken(token);
    assertEquals(timestamp, decoded);
  }
}
