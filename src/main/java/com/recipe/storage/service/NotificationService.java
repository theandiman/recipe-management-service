package com.recipe.storage.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.recipe.storage.dto.MarkNotificationsReadRequest;
import com.recipe.storage.dto.PagedNotificationResponse;
import com.recipe.storage.dto.SocialNotificationResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for social activity notifications.
 */
@Slf4j
@Service
public class NotificationService {

  @Autowired(required = false)
  private Firestore firestore;

  @Value("${firestore.collection.notifications:notifications}")
  private String notificationsCollection;

  private final ConcurrentHashMap<String, Map<String, Object>> mockNotifications =
      new ConcurrentHashMap<>();

  /**
   * Create social notification.
   */
  public void createNotification(String recipientUid, String actorUid, String actorName,
      String eventType, String targetRecipeId, String targetRecipeName, String contentSnippet) {
    if (recipientUid == null || recipientUid.isBlank() || recipientUid.equals(actorUid)) {
      return;
    }

    String id = UUID.randomUUID().toString();
    Instant now = Instant.now();

    Map<String, Object> data = new HashMap<>();
    data.put("id", id);
    data.put("recipientUid", recipientUid);
    data.put("actorUid", actorUid);
    data.put("actorName", actorName != null ? actorName : "Chef User");
    data.put("eventType", eventType);
    data.put("targetRecipeId", targetRecipeId);
    data.put("targetRecipeName", targetRecipeName);
    data.put("contentSnippet", contentSnippet);
    data.put("isRead", false);
    data.put("createdAt", now.toString());

    if (firestore == null) {
      mockNotifications.put(id, data);
      return;
    }

    try {
      firestore.collection(notificationsCollection).document(id).set(data);
      log.info("Created notification {} for user {}", id, recipientUid);
    } catch (Exception e) {
      log.error("Failed to create notification for user {}", recipientUid, e);
    }
  }

  /**
   * Get notifications.
   */
  public PagedNotificationResponse getNotifications(String userId, int page, int size) {
    if (firestore == null) {
      return getFromMockNotifications(userId);
    }

    try {
      Query query = firestore.collection(notificationsCollection)
          .whereEqualTo("recipientUid", userId);
      QuerySnapshot snapshot = query.get().get();

      List<SocialNotificationResponse> list = new ArrayList<>();
      int unreadCount = 0;

      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        Map<String, Object> data = doc.getData();
        if (data == null) {
          continue;
        }
        SocialNotificationResponse item = mapToResponse(data);
        if (Boolean.FALSE.equals(item.getIsRead())) {
          unreadCount++;
        }
        list.add(item);
      }

      list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

      int fromIndex = Math.min(page * size, list.size());
      int toIndex = Math.min(fromIndex + size, list.size());
      List<SocialNotificationResponse> paged = list.subList(fromIndex, toIndex);

      return PagedNotificationResponse.builder()
          .unreadCount(unreadCount)
          .notifications(paged)
          .hasMore(toIndex < list.size())
          .build();
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error fetching notifications for user {}", userId, e);
      throw new RuntimeException("Failed to fetch notifications", e);
    }
  }

  /**
   * Mark as read.
   */
  public void markAsRead(String userId, MarkNotificationsReadRequest request) {
    if (firestore == null) {
      mockNotifications.values().forEach(data -> {
        if (userId.equals(data.get("recipientUid"))) {
          if (request == null || request.getNotificationIds() == null
              || request.getNotificationIds().isEmpty()
              || request.getNotificationIds().contains(data.get("id"))) {
            data.put("isRead", true);
          }
        }
      });
      return;
    }

    try {
      Query query = firestore.collection(notificationsCollection)
          .whereEqualTo("recipientUid", userId);
      QuerySnapshot snapshot = query.get().get();

      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        String id = doc.getId();
        if (request == null || request.getNotificationIds() == null
            || request.getNotificationIds().isEmpty()
            || request.getNotificationIds().contains(id)) {
          doc.getReference().update("isRead", true);
        }
      }
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error marking notifications as read for user {}", userId, e);
      throw new RuntimeException("Failed to mark notifications as read", e);
    }
  }

  private SocialNotificationResponse mapToResponse(Map<String, Object> data) {
    return SocialNotificationResponse.builder()
        .id((String) data.get("id"))
        .recipientUid((String) data.get("recipientUid"))
        .actorUid((String) data.get("actorUid"))
        .actorName((String) data.get("actorName"))
        .actorAvatarUrl((String) data.get("actorAvatarUrl"))
        .eventType((String) data.get("eventType"))
        .targetRecipeId((String) data.get("targetRecipeId"))
        .targetRecipeName((String) data.get("targetRecipeName"))
        .contentSnippet((String) data.get("contentSnippet"))
        .isRead((Boolean) data.get("isRead"))
        .createdAt((String) data.get("createdAt"))
        .build();
  }

  private PagedNotificationResponse getFromMockNotifications(String userId) {
    List<SocialNotificationResponse> list = new ArrayList<>();
    int unread = 0;
    for (Map<String, Object> data : mockNotifications.values()) {
      if (userId.equals(data.get("recipientUid"))) {
        SocialNotificationResponse r = mapToResponse(data);
        if (Boolean.FALSE.equals(r.getIsRead())) {
          unread++;
        }
        list.add(r);
      }
    }
    return PagedNotificationResponse.builder()
        .unreadCount(unread)
        .notifications(list)
        .hasMore(false)
        .build();
  }
}
