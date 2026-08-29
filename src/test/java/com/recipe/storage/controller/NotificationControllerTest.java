package com.recipe.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recipe.storage.dto.MarkNotificationsReadRequest;
import com.recipe.storage.dto.PagedNotificationResponse;
import com.recipe.storage.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for NotificationController.
 */
public class NotificationControllerTest {

  private NotificationService notificationService;
  private NotificationController notificationController;

  @BeforeEach
  public void setUp() {
    notificationService = mock(NotificationService.class);
    notificationController = new NotificationController(notificationService);
  }

  @Test
  public void testGetNotifications() {
    PagedNotificationResponse expected = PagedNotificationResponse.builder().build();
    when(notificationService.getNotifications("user-1", 0, 20)).thenReturn(expected);

    ResponseEntity<PagedNotificationResponse> res =
        notificationController.getNotifications(0, 20, "user-1");
    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(expected, res.getBody());
  }

  @Test
  public void testMarkAsRead() {
    MarkNotificationsReadRequest req = MarkNotificationsReadRequest.builder().build();
    ResponseEntity<Void> res = notificationController.markAsRead(req, "user-1");
    assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
    verify(notificationService).markAsRead(eq("user-1"), any());
  }
}
