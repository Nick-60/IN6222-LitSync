# LitSync (IN6222 Individual Project)

LitSync is a native Android app for tracking and managing academic papers using the arXiv API.  
It provides paper browsing, search, bookmarking with metadata, daily recommendations via WorkManager, optional AI assistance (LLM), and Firebase cloud sync.

## Requirements / Environment
- **Android Studio** (recent version recommended)
- **JDK 11** (project is configured for Java 11)
- **Android SDK**:
  - `minSdk`: 26
  - `targetSdk`: 36
  - `compileSdk`: 36
- **Internet access** (the app calls arXiv API and AI/Firebase services)

## How to Run
1. Clone the repo:
   ```bash
   git clone https://github.com/Nick-60/IN6222-LitSync.git
   ```
2. Open the project in Android Studio.
3. Let Gradle sync dependencies.
4. Run the `app` configuration on an emulator or a physical Android device. *(Note: `google-services.json` is included in the repository, so the project will compile and run out of the box without extra Firebase configuration).*

## Features (User Functions)
- **Trending**: Browse recent arXiv papers by category, sort order, result size, and paging.
- **Search**: Search arXiv with optional AI query rewriting + AI insights summary for top results.
- **Bookmarks**: Save/remove papers, edit tags/groups/notes, filter by tag/group, swipe-to-delete with undo.
- **Recommendations**: Personalized recommendations from bookmarks; scheduled daily background run via WorkManager; manual refresh supported.
- **Language**: Follow System / English / Simplified Chinese.
- **Cloud Sync**: Login/register and sync your bookmarks to Firebase Firestore.

## AI Configuration (Optional)
AI features require an API key (and endpoint/model depending on provider).
This project reads AI config from Gradle properties or `local.properties`, and can also be adjusted in the in-app settings UI.

### Option A: Configure via `local.properties` (Recommended for local dev)
Create or update `local.properties` (which is not committed to git) in the project root:
```properties
AI_PROVIDER=BIGMODEL
AI_BASE_URL=https://open.bigmodel.cn/api/paas/v4/
AI_MODEL=glm-4.7-flash
AI_API_KEY=your_dev_key_here
```

### Expected Behavior Without AI Key
- Core app features (Trending / Search / Bookmarks / Recommendations) still work perfectly.
- AI-related actions will show “AI is not configured” (or similar) and will not call any LLM service.
