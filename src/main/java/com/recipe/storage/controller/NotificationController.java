package com.recipe.storage.controller;

import com.recipe.storage.dto.MarkNotificationsReadRequest;
import com.recipe.storage.dto.PagedNotificationResponse;
import com.recipe.storage.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for user notifications.
 */
@Slf4j
@RestController
@RequestMapping("/api/users/me/notifications")
@RequiredArgsConstructor
@Validated
@Tag(name = "Social Notifications",
    description = "APIs for user social notifications and activity stream")
@SecurityRequirement(name = "Firebase Auth")
public class NotificationController {

  private final NotificationService notificationService;

  /**
   * Get notifications.
   */
  @GetMapping
  @Operation(summary = "Get authenticated user's social notification feed")
  public ResponseEntity<PagedNotificationResponse> getNotifications(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String userId) {

    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    log.info("Fetching notifications for user {}", userId);
    PagedNotificationResponse response = notificationService.getNotifications(userId, page, size);
    return ResponseEntity.ok(response);
  }

  /**
   * Mark as read.
   */
  @PostMapping("/read")
  @Operation(summary = "Mark notifications as read")
  public ResponseEntity<Void> markAsRead(
      @RequestBody(required = false) MarkNotificationsReadRequest request,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String userId) {

    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    log.info("User {} marking notifications as read", userId);
    notificationService.markAsRead(userId, request);
    return ResponseEntity.noContent().build();
  }
}
