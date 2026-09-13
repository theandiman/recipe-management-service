package com.recipe.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import com.google.cloud.firestore.Transaction.Function;
import com.recipe.storage.dto.PagedRecipeResponse;
import com.recipe.storage.mapper.RecipeMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RecipeSocialServiceTest {

  @Mock
  private Firestore firestore;

  @Mock
  private FollowService followService;

  @Mock
  private NotificationService notificationService;

  @Mock
  private RecipeAuthorService recipeAuthorService;

  private final RecipeMapper recipeMapper = new RecipeMapper();

  private RecipeSocialService socialService;

  @BeforeEach
  void setUp() {
    socialService = new RecipeSocialService(
        firestore,
        followService,
        notificationService,
        recipeAuthorService,
        recipeMapper,
        "recipes",
        "savedRecipes",
        "likes"
    );
  }

  @Test
  void likeRecipe_FirestoreNotConfigured_ThrowsServiceUnavailable() {
    RecipeSocialService noDbService = new RecipeSocialService();
    assertThrows(ResponseStatusException.class, () -> noDbService.likeRecipe("rec-1", "user-1"));
  }

  @Test
  void unlikeRecipe_FirestoreNotConfigured_ThrowsServiceUnavailable() {
    RecipeSocialService noDbService = new RecipeSocialService();
    assertThrows(ResponseStatusException.class, () -> noDbService.unlikeRecipe("rec-1", "user-1"));
  }

  @Test
  void saveRecipeForUser_FirestoreNotConfigured_ThrowsServiceUnavailable() {
    RecipeSocialService noDbService = new RecipeSocialService();
    assertThrows(ResponseStatusException.class, () -> noDbService.saveRecipeForUser("rec-1", "user-1"));
  }

  @Test
  void unsaveRecipeForUser_FirestoreNotConfigured_ThrowsServiceUnavailable() {
    RecipeSocialService noDbService = new RecipeSocialService();
    assertThrows(ResponseStatusException.class, () -> noDbService.unsaveRecipeForUser("rec-1", "user-1"));
  }

  @Test
  void getFeed_FollowingNoOne_ReturnsEmpty() {
    when(followService.getFollowingIds("user-1")).thenReturn(List.of());
    PagedRecipeResponse feed = socialService.getFeed("user-1", null, 20);

    assertNotNull(feed);
    assertEquals(0, feed.getRecipes().size());
  }

  @Test
  void getFeed_InvalidPageSize_ThrowsBadRequest() {
    assertThrows(ResponseStatusException.class, () -> socialService.getFeed("user-1", null, 0));
    assertThrows(ResponseStatusException.class, () -> socialService.getFeed("user-1", null, 101));
  }

  @Test
  void getSavedRecipes_InvalidPageSize_ThrowsBadRequest() {
    assertThrows(ResponseStatusException.class, () -> socialService.getSavedRecipes("user-1", null, 0));
    assertThrows(ResponseStatusException.class, () -> socialService.getSavedRecipes("user-1", null, 101));
  }

  @Test
  void isRecipeLikedByUser_NoFirestore_ReturnsFalse() {
    RecipeSocialService noDbService = new RecipeSocialService();
    assertFalse(noDbService.isRecipeLikedByUser("rec-1", "user-1"));
  }

  @Test
  void isRecipeSavedByUser_NoFirestore_ReturnsFalse() {
    RecipeSocialService noDbService = new RecipeSocialService();
    assertFalse(noDbService.isRecipeSavedByUser("rec-1", "user-1"));
  }
}
