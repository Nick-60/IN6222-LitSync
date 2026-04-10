package com.in6222.litsync.recommendation;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RecommendationScheduler {

    public static final String UNIQUE_DAILY_WORK = "daily_recommendation_work";
    public static final String UNIQUE_IMMEDIATE_WORK = "refresh_recommendation_work";

    private RecommendationScheduler() {
    }

    public static void scheduleDaily(Context context, int hour, int minute) {
        Context appContext = context.getApplicationContext();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(RecommendationWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInputData(new Data.Builder()
                        .putBoolean(RecommendationWorker.KEY_SHOW_NOTIFICATION, true)
                        .putBoolean(RecommendationWorker.KEY_FORCE_RUN, false)
                        .build())
                .setInitialDelay(calculateInitialDelay(hour, minute), TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                UNIQUE_DAILY_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        );
    }

    public static void cancelDaily(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(UNIQUE_DAILY_WORK);
    }

    public static UUID enqueueImmediateRefresh(Context context, boolean showNotification, boolean forceRun) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RecommendationWorker.class)
                .setConstraints(constraints)
                .setInputData(new Data.Builder()
                        .putBoolean(RecommendationWorker.KEY_SHOW_NOTIFICATION, showNotification)
                        .putBoolean(RecommendationWorker.KEY_FORCE_RUN, forceRun)
                        .build())
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                workRequest
        );
        return workRequest.getId();
    }

    private static long calculateInitialDelay(int hour, int minute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).toMillis();
    }
}
