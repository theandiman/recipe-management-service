package com.recipe.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.recipe.storage.service.RecipeAuthorService.AuthorInfo;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecipeAuthorServiceTest {

  @Mock
  private Firestore firestore;

  @Mock
  private FirebaseAuth firebaseAuth;

  private RecipeAuthorService authorService;

  @BeforeEach
  void setUp() {
    authorService = new RecipeAuthorService();
    ReflectionTestUtils.setField(authorService, "firestore", firestore);
    ReflectionTestUtils.setField(authorService, "firebaseAuth", firebaseAuth);
    ReflectionTestUtils.setField(authorService, "usersCollection", "users");
  }

  @Test
  void resolveAuthorInfo_NullUserId_ReturnsNulls() {
    AuthorInfo info = authorService.resolveAuthorInfo(null);
    assertNull(info.displayName());
    assertNull(info.avatarUrl());
  }

  @Test
  void resolveAuthorInfo_FromFirestoreProfile() throws Exception {
    CollectionReference collection = mock(CollectionReference.class);
    DocumentReference docRef = mock(DocumentReference.class);
    @SuppressWarnings("unchecked")
    ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);
    DocumentSnapshot doc = mock(DocumentSnapshot.class);

    when(firestore.collection("users")).thenReturn(collection);
    when(collection.document("user-1")).thenReturn(docRef);
    when(docRef.get()).thenReturn(future);
    when(future.get()).thenReturn(doc);
    when(doc.exists()).thenReturn(true);
    when(doc.getString("displayName")).thenReturn("Jane Cook");
    when(doc.getString("avatarUrl")).thenReturn("https://example.com/avatar.jpg");

    AuthorInfo info = authorService.resolveAuthorInfo("user-1");
    assertEquals("Jane Cook", info.displayName());
    assertEquals("https://example.com/avatar.jpg", info.avatarUrl());
  }

  @Test
  void resolveAuthorInfo_FallbackToFirebaseAuth() throws Exception {
    CollectionReference collection = mock(CollectionReference.class);
    DocumentReference docRef = mock(DocumentReference.class);
    @SuppressWarnings("unchecked")
    ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);
    DocumentSnapshot doc = mock(DocumentSnapshot.class);
    UserRecord userRecord = mock(UserRecord.class);

    when(firestore.collection("users")).thenReturn(collection);
    when(collection.document("user-2")).thenReturn(docRef);
    when(docRef.get()).thenReturn(future);
    when(future.get()).thenReturn(doc);
    when(doc.exists()).thenReturn(false);

    when(firebaseAuth.getUser("user-2")).thenReturn(userRecord);
    when(userRecord.getDisplayName()).thenReturn("Auth Chef");
    when(userRecord.getPhotoUrl()).thenReturn("https://example.com/photo.jpg");

    AuthorInfo info = authorService.resolveAuthorInfo("user-2");
    assertEquals("Auth Chef", info.displayName());
    assertEquals("https://example.com/photo.jpg", info.avatarUrl());
    assertEquals("Auth Chef", authorService.resolveDisplayName("user-2"));
    assertEquals("https://example.com/photo.jpg", authorService.resolveAvatarUrl("user-2"));
  }
}
