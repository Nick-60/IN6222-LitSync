package com.in6222.litsync.firebase;

import android.content.Context;
import android.net.Uri;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.in6222.litsync.database.FavoritePaper;
import com.in6222.litsync.model.PaperItem;
import com.in6222.litsync.ui.BookmarkMetadataStore;

import java.util.ArrayList;
import java.util.List;

public class FirebaseSyncManager {

    private final Context context;
    private final BookmarkMetadataStore metadataStore;

    public FirebaseSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.metadataStore = new BookmarkMetadataStore(this.context);
    }

    public boolean isAvailable() {
        return !FirebaseApp.getApps(context).isEmpty();
    }

    public FirebaseUser getCurrentUser() {
        try {
            return getAuth().getCurrentUser();
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    public Task<AuthResult> register(String email, String password) {
        return getAuth().createUserWithEmailAndPassword(email, password);
    }

    public Task<AuthResult> signIn(String email, String password) {
        return getAuth().signInWithEmailAndPassword(email, password);
    }

    public void signOut() {
        getAuth().signOut();
    }

    public Task<Void> syncBookmarks(List<FavoritePaper> favorites) {
        FirebaseUser user = requireUser();
        CollectionReference collectionReference = getFirestore()
                .collection("users")
                .document(user.getUid())
                .collection("bookmarks");

        WriteBatch batch = getFirestore().batch();
        if (favorites != null) {
            for (FavoritePaper favorite : favorites) {
                FirebaseBookmarkRecord record = toRecord(favorite);
                batch.set(collectionReference.document(getDocumentId(record)), record);
            }
        }
        return batch.commit();
    }

    public Task<List<FirebaseBookmarkRecord>> fetchBookmarks() {
        FirebaseUser user = requireUser();
        return getFirestore()
                .collection("users")
                .document(user.getUid())
                .collection("bookmarks")
                .get()
                .continueWith(task -> {
                    QuerySnapshot snapshot = task.getResult();
                    List<FirebaseBookmarkRecord> records = new ArrayList<>();
                    if (snapshot == null) {
                        return records;
                    }
                    for (com.google.firebase.firestore.DocumentSnapshot documentSnapshot : snapshot.getDocuments()) {
                        FirebaseBookmarkRecord record = documentSnapshot.toObject(FirebaseBookmarkRecord.class);
                        if (record != null) {
                            records.add(record);
                        }
                    }
                    return records;
                });
    }

    private FirebaseBookmarkRecord toRecord(FavoritePaper favorite) {
        FirebaseBookmarkRecord record = new FirebaseBookmarkRecord();
        record.setTitle(favorite.getTitle());
        record.setSummary(favorite.getSummary());
        record.setAuthor(favorite.getAuthor());
        record.setLink(favorite.getLink());
        record.setPublishedDate(favorite.getPublishedDate());
        PaperItem item = new PaperItem(
                favorite.getId(),
                favorite.getTitle(),
                favorite.getAuthor(),
                favorite.getSummary(),
                favorite.getPublishedDate(),
                favorite.getLink()
        );
        record.setNote(metadataStore.getNote(item));
        record.setTags(metadataStore.getTags(item));
        record.setGroup(metadataStore.getGroup(item));
        record.setSyncedAt(System.currentTimeMillis());
        return record;
    }

    private FirebaseAuth getAuth() {
        if (!isAvailable()) {
            throw new IllegalStateException("Firebase is not configured");
        }
        return FirebaseAuth.getInstance();
    }

    private FirebaseFirestore getFirestore() {
        if (!isAvailable()) {
            throw new IllegalStateException("Firebase is not configured");
        }
        return FirebaseFirestore.getInstance();
    }

    private FirebaseUser requireUser() {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("User is not signed in");
        }
        return user;
    }

    private String getDocumentId(FirebaseBookmarkRecord record) {
        String link = record.getLink() == null ? "" : record.getLink().trim();
        if (!link.isEmpty()) {
            return Uri.encode(link);
        }
        String title = record.getTitle() == null ? "" : record.getTitle().trim();
        String publishedDate = record.getPublishedDate() == null ? "" : record.getPublishedDate().trim();
        return Uri.encode(title + "|" + publishedDate);
    }
}
