package com.in6222.litsync.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.in6222.litsync.R;
import com.in6222.litsync.ai.AiConfig;
import com.in6222.litsync.ai.AiProviderPreset;
import com.in6222.litsync.ai.AiSettingsStore;
import com.in6222.litsync.database.AppDatabase;
import com.in6222.litsync.databinding.ActivityAiSettingsBinding;
import com.in6222.litsync.firebase.FirebaseBookmarkRecord;
import com.in6222.litsync.firebase.FirebaseSettingsStore;
import com.in6222.litsync.firebase.FirebaseSyncManager;
import com.in6222.litsync.network.RetrofitClient;
import com.in6222.litsync.recommendation.RecommendationScheduler;
import com.in6222.litsync.recommendation.RecommendationSettingsStore;
import com.in6222.litsync.recommendation.RecommendationStore;
import com.in6222.litsync.recommendation.RecommendationWorker;
import com.in6222.litsync.repository.PaperRepository;
import com.in6222.litsync.util.AppLanguageManager;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiSettingsActivity extends AppCompatActivity {

    public static final String EXTRA_LAUNCH_LANGUAGE_TAG = "launch_language_tag";
    public static final String EXTRA_LANGUAGE_CHANGED = "language_changed";
    private static final int RECOMMENDATION_MINUTE_STEP = 5;
    private static final String[] RECOMMENDATION_MINUTE_VALUES = {
            "00", "05", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55"
    };

    private ActivityAiSettingsBinding binding;
    private AiSettingsStore settingsStore;
    private FirebaseSettingsStore firebaseSettingsStore;
    private FirebaseSyncManager firebaseSyncManager;
    private BookmarkMetadataStore bookmarkMetadataStore;
    private PaperRepository repository;
    private ExecutorService executor;
    private List<AiProviderPreset> presets;
    private AiProviderPreset selectedPreset;
    private RecommendationSettingsStore recommendationSettingsStore;
    private RecommendationStore recommendationStore;
    private List<LanguageOption> languageOptions;
    private String launchLanguageTag = "";
    private int selectedRecommendationHour;
    private int selectedRecommendationMinute;
    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (!isGranted && binding != null) {
                    Snackbar.make(binding.getRoot(), R.string.message_recommendation_permission_needed, Snackbar.LENGTH_LONG).show();
                }
                updateRecommendationStatus();
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppLanguageManager.applySavedLanguage(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityAiSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        settingsStore = new AiSettingsStore(this);
        firebaseSettingsStore = new FirebaseSettingsStore(this);
        firebaseSyncManager = new FirebaseSyncManager(this);
        bookmarkMetadataStore = new BookmarkMetadataStore(this);
        recommendationSettingsStore = new RecommendationSettingsStore(this);
        recommendationStore = new RecommendationStore(this);
        executor = Executors.newSingleThreadExecutor();
        AppDatabase database = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "litsync.db").build();
        repository = new PaperRepository(RetrofitClient.getService(), database);
        presets = AiProviderPreset.defaults();
        launchLanguageTag = getIntent().getStringExtra(EXTRA_LAUNCH_LANGUAGE_TAG);
        if (launchLanguageTag == null) {
            launchLanguageTag = AppLanguageManager.getSavedLanguageTag(this);
        }
        selectedRecommendationHour = recommendationSettingsStore.getHour();
        selectedRecommendationMinute = normalizeRecommendationMinute(recommendationSettingsStore.getMinute());

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        binding.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        applyWindowInsets();
        setupLanguageInput();
        setupPresetInput();
        bindSavedValues();
        bindFirebaseValues();
        bindRecommendationValues();
        binding.saveButton.setOnClickListener(v -> saveSettings());
        binding.resetButton.setOnClickListener(v -> resetToDefaults());
        binding.registerFirebaseButton.setOnClickListener(v -> registerFirebaseAccount());
        binding.signInFirebaseButton.setOnClickListener(v -> signInFirebase());
        binding.signOutFirebaseButton.setOnClickListener(v -> signOutFirebase());
        binding.syncCloudButton.setOnClickListener(v -> syncToCloud());
        binding.restoreCloudButton.setOnClickListener(v -> restoreFromCloud());
        binding.recommendationEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updateRecommendationStatus());
        binding.pickRecommendationTimeButton.setOnClickListener(v -> showRecommendationTimePicker());
        binding.saveRecommendationSettingsButton.setOnClickListener(v -> saveRecommendationSettings());
        binding.refreshRecommendationsButton.setOnClickListener(v -> refreshRecommendations(true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRecommendationStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
        binding = null;
    }

    private void setupPresetInput() {
        List<String> labels = new ArrayList<>();
        for (AiProviderPreset preset : presets) {
            labels.add(preset.getDisplayName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        binding.providerInput.setAdapter(adapter);
        binding.providerInput.setOnItemClickListener((parent, view, position, id) -> applyPreset(presets.get(position), true));
    }

    private void setupLanguageInput() {
        languageOptions = new ArrayList<>();
        languageOptions.add(new LanguageOption("", getString(R.string.option_language_system)));
        languageOptions.add(new LanguageOption("en", getString(R.string.option_language_english)));
        languageOptions.add(new LanguageOption("zh", getString(R.string.option_language_chinese_simplified)));
        List<String> labels = new ArrayList<>();
        for (LanguageOption option : languageOptions) {
            labels.add(option.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        binding.languageInput.setAdapter(adapter);
        binding.languageInput.setText(findLanguageLabel(AppLanguageManager.getSavedLanguageTag(this)), false);
        binding.languageInput.setOnItemClickListener((parent, view, position, id) -> applyLanguage(languageOptions.get(position)));
    }

    private void bindSavedValues() {
        AiConfig currentConfig = settingsStore.load();
        String presetId = settingsStore.getPresetId();
        selectedPreset = AiProviderPreset.findById(presetId);
        binding.providerInput.setText(selectedPreset.getDisplayName(), false);
        binding.apiKeyInput.setText(currentConfig.getApiKey());
        if (selectedPreset.getId().equals(AiProviderPreset.ID_CUSTOM)) {
            binding.baseUrlInput.setText(currentConfig.getRawBaseUrl());
            binding.modelInput.setText(currentConfig.getModel());
            updatePresetDetail(selectedPreset);
            return;
        }
        AiProviderPreset matchedPreset = findMatchingPreset(currentConfig);
        if (matchedPreset != null) {
            selectedPreset = matchedPreset;
            binding.providerInput.setText(selectedPreset.getDisplayName(), false);
            binding.baseUrlInput.setText(selectedPreset.getBaseUrl());
            binding.modelInput.setText(selectedPreset.getModel());
        } else {
            binding.baseUrlInput.setText(currentConfig.getRawBaseUrl());
            binding.modelInput.setText(currentConfig.getModel());
        }
        updatePresetDetail(selectedPreset);
    }

    private void applyLanguage(LanguageOption option) {
        String currentLanguageTag = AppLanguageManager.getSavedLanguageTag(this);
        if (currentLanguageTag.equals(option.tag)) {
            binding.languageInput.setText(option.label, false);
            return;
        }
        binding.languageInput.setText(option.label, false);
        AppLanguageManager.setLanguage(this, option.tag);
    }

    private void applyPreset(AiProviderPreset preset, boolean overwriteValues) {
        selectedPreset = preset;
        if (overwriteValues) {
            binding.baseUrlInput.setText(preset.getBaseUrl());
            binding.modelInput.setText(preset.getModel());
        }
        updatePresetDetail(preset);
    }

    private void updatePresetDetail(AiProviderPreset preset) {
        String detail = getString(
                R.string.message_ai_provider_detail,
                preset.getDisplayName(),
                preset.getProviderType().name(),
                preset.getBaseUrl().isEmpty() ? getString(R.string.value_custom_required) : preset.getBaseUrl(),
                preset.getModel().isEmpty() ? getString(R.string.value_custom_required) : preset.getModel()
        );
        binding.providerDetailText.setText(detail);
    }

    private void saveSettings() {
        String apiKey = textOf(binding.apiKeyInput);
        String baseUrl = textOf(binding.baseUrlInput);
        String model = textOf(binding.modelInput);
        if (selectedPreset == null) {
            selectedPreset = presets.get(0);
        }
        if (apiKey.isEmpty() || baseUrl.isEmpty() || model.isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.message_ai_settings_required, Snackbar.LENGTH_LONG).show();
            return;
        }
        AiConfig config = new AiConfig(selectedPreset.getProviderType(), baseUrl, model, apiKey);
        settingsStore.save(selectedPreset.getId(), config);
        Snackbar.make(binding.getRoot(), R.string.message_ai_settings_saved, Snackbar.LENGTH_SHORT).show();
        binding.getRoot().postDelayed(this::finish, 250);
    }

    private void resetToDefaults() {
        selectedPreset = AiProviderPreset.findById(AiProviderPreset.ID_GLM);
        binding.providerInput.setText(selectedPreset.getDisplayName(), false);
        binding.apiKeyInput.setText("");
        binding.baseUrlInput.setText(selectedPreset.getBaseUrl());
        binding.modelInput.setText(selectedPreset.getModel());
        updatePresetDetail(selectedPreset);
    }

    private void bindFirebaseValues() {
        binding.firebaseEmailInput.setText(firebaseSettingsStore.loadEmail());
        updateFirebaseStatus();
    }

    private void bindRecommendationValues() {
        binding.recommendationEnabledSwitch.setChecked(recommendationSettingsStore.isEnabled());
        updateRecommendationTimeText();
        updateRecommendationStatus();
    }

    private void showRecommendationTimePicker() {
        boolean use24Hour = android.text.format.DateFormat.is24HourFormat(this);
        NumberPicker hourPicker = new NumberPicker(this);
        NumberPicker minutePicker = new NumberPicker(this);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(RECOMMENDATION_MINUTE_VALUES.length - 1);
        minutePicker.setDisplayedValues(RECOMMENDATION_MINUTE_VALUES);
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setValue(normalizeRecommendationMinute(selectedRecommendationMinute) / RECOMMENDATION_MINUTE_STEP);

        NumberPicker periodPicker = null;
        if (use24Hour) {
            hourPicker.setMinValue(0);
            hourPicker.setMaxValue(23);
            hourPicker.setFormatter(value -> String.format(java.util.Locale.getDefault(), "%02d", value));
            hourPicker.setValue(selectedRecommendationHour);
        } else {
            hourPicker.setMinValue(1);
            hourPicker.setMaxValue(12);
            int displayHour = selectedRecommendationHour % 12;
            if (displayHour == 0) {
                displayHour = 12;
            }
            hourPicker.setValue(displayHour);
            periodPicker = new NumberPicker(this);
            periodPicker.setMinValue(0);
            periodPicker.setMaxValue(1);
            periodPicker.setDisplayedValues(new String[]{
                    getString(R.string.option_period_am),
                    getString(R.string.option_period_pm)
            });
            periodPicker.setWrapSelectorWheel(false);
            periodPicker.setValue(selectedRecommendationHour >= 12 ? 1 : 0);
        }

        hourPicker.setWrapSelectorWheel(true);

        LinearLayout pickersRow = new LinearLayout(this);
        pickersRow.setOrientation(LinearLayout.HORIZONTAL);
        pickersRow.addView(createPickerColumn(getString(R.string.label_hour), hourPicker));
        pickersRow.addView(createPickerColumn(getString(R.string.label_minute), minutePicker));
        if (periodPicker != null) {
            pickersRow.addView(createPickerColumn(getString(R.string.label_period), periodPicker));
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(20);
        int verticalPadding = dp(8);
        container.setPadding(horizontalPadding, verticalPadding, horizontalPadding, 0);

        TextView hintText = new TextView(this);
        hintText.setText(R.string.message_recommendation_time_picker_hint);
        hintText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        container.addView(hintText);
        container.addView(pickersRow);

        NumberPicker finalPeriodPicker = periodPicker;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_pick_recommendation_time)
                .setView(container)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    selectedRecommendationMinute = RECOMMENDATION_MINUTE_STEP * minutePicker.getValue();
                    if (use24Hour) {
                        selectedRecommendationHour = hourPicker.getValue();
                    } else {
                        int hour = hourPicker.getValue() % 12;
                        selectedRecommendationHour = hour + ((finalPeriodPicker != null && finalPeriodPicker.getValue() == 1) ? 12 : 0);
                    }
                    updateRecommendationTimeText();
                    updateRecommendationStatus();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveRecommendationSettings() {
        boolean enabled = binding.recommendationEnabledSwitch.isChecked();
        selectedRecommendationMinute = normalizeRecommendationMinute(selectedRecommendationMinute);
        recommendationSettingsStore.save(enabled, selectedRecommendationHour, selectedRecommendationMinute);
        if (enabled) {
            RecommendationScheduler.scheduleDaily(this, selectedRecommendationHour, selectedRecommendationMinute);
            requestNotificationPermissionIfNeeded();
            refreshRecommendations(false);
        } else {
            RecommendationScheduler.cancelDaily(this);
        }
        updateRecommendationStatus();
        Snackbar.make(binding.getRoot(), R.string.message_recommendation_schedule_saved, Snackbar.LENGTH_SHORT).show();
    }

    private void refreshRecommendations(boolean showStartedMessage) {
        if (showStartedMessage) {
            Snackbar.make(binding.getRoot(), R.string.message_recommendations_loading, Snackbar.LENGTH_SHORT).show();
        }
        binding.refreshRecommendationsButton.setEnabled(false);
        UUID workId = RecommendationScheduler.enqueueImmediateRefresh(this, false, true);
        WorkManager workManager = WorkManager.getInstance(getApplicationContext());
        Observer<WorkInfo> observer = new Observer<>() {
            @Override
            public void onChanged(WorkInfo workInfo) {
                if (workInfo == null || !workInfo.getState().isFinished()) {
                    return;
                }
                workManager.getWorkInfoByIdLiveData(workId).removeObserver(this);
                binding.refreshRecommendationsButton.setEnabled(true);
                updateRecommendationStatus();
                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                    Snackbar.make(binding.getRoot(), R.string.message_recommendations_updated, Snackbar.LENGTH_SHORT).show();
                    return;
                }
                String errorMessage = workInfo.getOutputData().getString(RecommendationWorker.KEY_ERROR_MESSAGE);
                if (errorMessage == null || errorMessage.trim().isEmpty()) {
                    errorMessage = getString(R.string.message_recommendation_worker_failed);
                }
                Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();
            }
        };
        workManager.getWorkInfoByIdLiveData(workId).observe(this, observer);
    }

    private void registerFirebaseAccount() {
        if (!validateFirebaseForm(true)) {
            return;
        }
        firebaseSettingsStore.saveEmail(textOf(binding.firebaseEmailInput));
        firebaseSyncManager.register(textOf(binding.firebaseEmailInput), textOf(binding.firebasePasswordInput))
                .addOnSuccessListener(result -> {
                    Snackbar.make(binding.getRoot(), R.string.message_firebase_auth_success, Snackbar.LENGTH_SHORT).show();
                    updateFirebaseStatus();
                })
                .addOnFailureListener(this::showFirebaseError);
    }

    private void signInFirebase() {
        if (!validateFirebaseForm(true)) {
            return;
        }
        firebaseSettingsStore.saveEmail(textOf(binding.firebaseEmailInput));
        firebaseSyncManager.signIn(textOf(binding.firebaseEmailInput), textOf(binding.firebasePasswordInput))
                .addOnSuccessListener(result -> {
                    Snackbar.make(binding.getRoot(), R.string.message_firebase_auth_success, Snackbar.LENGTH_SHORT).show();
                    updateFirebaseStatus();
                })
                .addOnFailureListener(this::showFirebaseError);
    }

    private void signOutFirebase() {
        try {
            firebaseSyncManager.signOut();
            Snackbar.make(binding.getRoot(), R.string.message_firebase_signed_out_success, Snackbar.LENGTH_SHORT).show();
            updateFirebaseStatus();
        } catch (Exception exception) {
            showFirebaseError(exception);
        }
    }

    private void syncToCloud() {
        if (!validateFirebaseForm(false)) {
            return;
        }
        Snackbar.make(binding.getRoot(), R.string.message_firebase_syncing, Snackbar.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                List<com.in6222.litsync.database.FavoritePaper> favorites = repository.getAllFavorites();
                runOnUiThread(() -> firebaseSyncManager.syncBookmarks(favorites)
                        .addOnSuccessListener(unused -> Snackbar.make(binding.getRoot(), R.string.message_firebase_sync_success, Snackbar.LENGTH_SHORT).show())
                        .addOnFailureListener(this::showFirebaseError));
            } catch (Exception exception) {
                runOnUiThread(() -> showFirebaseError(exception));
            }
        });
    }

    private void restoreFromCloud() {
        if (!validateFirebaseForm(false)) {
            return;
        }
        Snackbar.make(binding.getRoot(), R.string.message_firebase_restoring, Snackbar.LENGTH_SHORT).show();
        firebaseSyncManager.fetchBookmarks()
                .addOnSuccessListener(records -> {
                    if (records.isEmpty()) {
                        Snackbar.make(binding.getRoot(), R.string.message_firebase_cloud_empty, Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    executor.execute(() -> {
                        try {
                            restoreRecordsToLocal(records);
                            runOnUiThread(() -> Snackbar.make(binding.getRoot(), R.string.message_firebase_restore_success, Snackbar.LENGTH_SHORT).show());
                        } catch (Exception exception) {
                            runOnUiThread(() -> showFirebaseError(exception));
                        }
                    });
                })
                .addOnFailureListener(this::showFirebaseError);
    }

    private void restoreRecordsToLocal(List<FirebaseBookmarkRecord> records) {
        for (FirebaseBookmarkRecord record : records) {
            com.in6222.litsync.database.FavoritePaper favorite = repository.getFavoriteByLink(record.getLink());
            if (favorite == null) {
                favorite = new com.in6222.litsync.database.FavoritePaper();
            }
            favorite.setTitle(record.getTitle());
            favorite.setAuthor(record.getAuthor());
            favorite.setSummary(record.getSummary());
            favorite.setPublishedDate(record.getPublishedDate());
            favorite.setLink(record.getLink());
            long savedId = repository.saveFavorite(favorite);
            favorite.setId((int) savedId);

            com.in6222.litsync.model.PaperItem paperItem = new com.in6222.litsync.model.PaperItem(
                    favorite.getId(),
                    favorite.getTitle(),
                    favorite.getAuthor(),
                    favorite.getSummary(),
                    favorite.getPublishedDate(),
                    favorite.getLink()
            );
            bookmarkMetadataStore.saveNote(paperItem, record.getNote());
            bookmarkMetadataStore.saveTags(paperItem, record.getTags());
            bookmarkMetadataStore.saveGroup(paperItem, record.getGroup());
        }
    }

    private void updateFirebaseStatus() {
        FirebaseUser currentUser = firebaseSyncManager.getCurrentUser();
        if (!firebaseSyncManager.isAvailable()) {
            binding.firebaseStatusText.setText(R.string.message_firebase_not_configured);
            return;
        }
        if (currentUser == null) {
            binding.firebaseStatusText.setText(R.string.message_firebase_signed_out);
            return;
        }
        binding.firebaseStatusText.setText(getString(R.string.message_firebase_signed_in, currentUser.getEmail()));
    }

    private void updateRecommendationTimeText() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, selectedRecommendationHour);
        calendar.set(Calendar.MINUTE, selectedRecommendationMinute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        binding.recommendationTimeText.setText(android.text.format.DateFormat.getTimeFormat(this).format(calendar.getTime()));
    }

    private void updateRecommendationStatus() {
        if (binding == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        if (binding.recommendationEnabledSwitch.isChecked()) {
            builder.append(getString(R.string.message_recommendations_enabled))
                    .append('\n')
                    .append(getString(R.string.message_recommendation_time_approx, binding.recommendationTimeText.getText()));
        } else {
            builder.append(getString(R.string.message_recommendations_disabled));
        }
        long generatedAt = recommendationStore.getGeneratedAt();
        builder.append('\n');
        if (generatedAt > 0L) {
            DateFormat dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
            builder.append(getString(R.string.message_recommendation_last_updated, dateTimeFormat.format(new Date(generatedAt))));
        } else {
            builder.append(getString(R.string.message_recommendation_never_updated));
        }
        if (binding.recommendationEnabledSwitch.isChecked() && isNotificationPermissionMissing()) {
            builder.append('\n').append(getString(R.string.message_recommendation_permission_needed));
        }
        binding.recommendationStatusText.setText(builder.toString());
    }

    private LinearLayout createPickerColumn(String label, NumberPicker picker) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        column.setLayoutParams(columnParams);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        column.addView(labelView);

        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pickerParams.topMargin = dp(8);
        picker.setLayoutParams(pickerParams);
        column.addView(picker);
        return column;
    }

    private int normalizeRecommendationMinute(int minute) {
        if (minute <= 0) {
            return 0;
        }
        return Math.min(55, (minute / RECOMMENDATION_MINUTE_STEP) * RECOMMENDATION_MINUTE_STEP);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (isNotificationPermissionMissing()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private boolean isNotificationPermissionMissing() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    private boolean validateFirebaseForm(boolean requirePassword) {
        boolean hasEmail = !textOf(binding.firebaseEmailInput).isEmpty();
        boolean hasPassword = !textOf(binding.firebasePasswordInput).isEmpty();
        if (!firebaseSyncManager.isAvailable()) {
            Snackbar.make(binding.getRoot(), R.string.message_firebase_not_configured, Snackbar.LENGTH_LONG).show();
            return false;
        }
        if (!hasEmail || (requirePassword && !hasPassword)) {
            Snackbar.make(binding.getRoot(), R.string.message_firebase_config_required, Snackbar.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void showFirebaseError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = getString(R.string.status_network_error);
        }
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private AiProviderPreset findMatchingPreset(AiConfig config) {
        for (AiProviderPreset preset : presets) {
            if (preset.getProviderType() == config.getProviderType()
                    && preset.getBaseUrl().equals(config.getRawBaseUrl())
                    && preset.getModel().equals(config.getModel())) {
                return preset;
            }
        }
        return null;
    }

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    systemBars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom()
            );
            return insets;
        });
    }

    private String findLanguageLabel(String languageTag) {
        for (LanguageOption option : languageOptions) {
            if (option.tag.equals(languageTag)) {
                return option.label;
            }
        }
        return languageOptions.get(0).label;
    }

    @Override
    public void finish() {
        String currentLanguageTag = AppLanguageManager.getSavedLanguageTag(this);
        if (!currentLanguageTag.equals(launchLanguageTag)) {
            Intent data = new Intent();
            data.putExtra(EXTRA_LANGUAGE_CHANGED, true);
            setResult(RESULT_OK, data);
        }
        super.finish();
    }

    private static final class LanguageOption {
        private final String tag;
        private final String label;

        private LanguageOption(String tag, String label) {
            this.tag = tag;
            this.label = label;
        }
    }
}
