package com.recipe.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.recipe.storage.dto.MarkNotificationsReadRequest;
import com.recipe.storage.dto.PagedNotificationResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for NotificationService.
 */
public class NotificationServiceTest {

  private NotificationService notificationService;

  @BeforeEach
  public void setUp() {
    notificationService = new NotificationService();
  }

  @Test
  public void testCreateAndMarkNotificationInMockStore() {
    notificationService.createNotification(
        "user-target",
        "user-actor",
        "Chef Actor",
        "RECIPE_LIKE",
        "rec-301",
        "Pasta Carbonara",
        "Liked your recipe"
    );

    PagedNotificationResponse res = notificationService.getNotifications("user-target", 0, 10);
    assertEquals(1, res.getUnreadCount());
    assertFalse(res.getNotifications().isEmpty());

    MarkNotificationsReadRequest markReq = MarkNotificationsReadRequest.builder()
        .notificationIds(List.of(res.getNotifications().get(0).getId()))
        .build();
    notificationService.markAsRead("user-target", markReq);

    PagedNotificationResponse readRes = notificationService.getNotifications("user-target", 0, 10);
    assertEquals(0, readRes.getUnreadCount());
  }
}
