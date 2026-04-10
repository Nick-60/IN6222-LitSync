package com.in6222.litsync.recommendation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.in6222.litsync.database.AppDatabase;
import com.in6222.litsync.database.FavoritePaper;
import com.in6222.litsync.network.RetrofitClient;
import com.in6222.litsync.repository.PaperRepository;

import java.util.List;

public class RecommendationWorker extends Worker {

    public static final String KEY_SHOW_NOTIFICATION = "show_notification";
    public static final String KEY_FORCE_RUN = "force_run";
    public static final String KEY_RESULT_COUNT = "result_count";
    public static final String KEY_ERROR_MESSAGE = "error_message";

    public RecommendationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        RecommendationSettingsStore settingsStore = new RecommendationSettingsStore(context);
        boolean forceRun = getInputData().getBoolean(KEY_FORCE_RUN, false);
        boolean showNotification = getInputData().getBoolean(KEY_SHOW_NOTIFICATION, true);
        if (!forceRun && !settingsStore.isEnabled()) {
            return Result.success();
        }

        AppDatabase database = null;
        try {
            database = Room.databaseBuilder(context, AppDatabase.class, "litsync.db").build();
            PaperRepository repository = new PaperRepository(RetrofitClient.getService(), database);
            List<FavoritePaper> favorites = repository.getAllFavorites();
            RecommendationStore recommendationStore = new RecommendationStore(context);
            RecommendationEngine recommendationEngine = new RecommendationEngine(context);
            List<RecommendedPaper> recommendations = recommendationEngine.generate(favorites);
            recommendationStore.saveRecommendations(recommendations);
            if (showNotification && !recommendations.isEmpty()) {
                boolean notificationShown = RecommendationNotificationHelper.showRecommendationNotification(context, recommendations);
                if (notificationShown) {
                    recommendationStore.addNotifiedRecommendations(recommendations);
                }
            }
            return Result.success(new Data.Builder()
                    .putInt(KEY_RESULT_COUNT, recommendations.size())
                    .build());
        } catch (Exception exception) {
            if (forceRun) {
                String message = exception.getMessage() == null ? "" : exception.getMessage();
                return Result.failure(new Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, message)
                        .build());
            }
            return Result.retry();
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }
}
