package com.in6222.litsync;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.in6222.litsync.databinding.ActivityMainBinding;
import com.in6222.litsync.recommendation.RecommendationNotificationHelper;
import com.in6222.litsync.recommendation.RecommendationScheduler;
import com.in6222.litsync.recommendation.RecommendationSettingsStore;
import com.in6222.litsync.ui.AiSettingsActivity;
import com.in6222.litsync.ui.TabPagerAdapter;
import com.in6222.litsync.ui.TrendingFragment;
import com.in6222.litsync.util.AppLanguageManager;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_START_TAB = "start_tab";
    public static final int TAB_TRENDING = 0;
    public static final int TAB_SEARCH = 1;
    public static final int TAB_BOOKMARKS = 2;
    public static final int TAB_RECOMMENDATIONS = 3;

    private ActivityMainBinding binding;
    private long lastBackPressedAt;
    private final ActivityResultLauncher<Intent> aiSettingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                if (result.getData().getBooleanExtra(AiSettingsActivity.EXTRA_LANGUAGE_CHANGED, false)) {
                    recreate();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLanguageManager.applySavedLanguage(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_ai_settings) {
                Intent intent = new Intent(this, AiSettingsActivity.class);
                intent.putExtra(AiSettingsActivity.EXTRA_LAUNCH_LANGUAGE_TAG, AppLanguageManager.getSavedLanguageTag(this));
                aiSettingsLauncher.launch(intent);
                return true;
            }
            return false;
        });

        TabPagerAdapter adapter = new TabPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);
        binding.viewPager.setOffscreenPageLimit(3);
        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            if (position == TAB_TRENDING) {
                tab.setText(R.string.tab_trending);
                tab.setIcon(android.R.drawable.ic_menu_sort_by_size);
            } else if (position == TAB_SEARCH) {
                tab.setText(R.string.tab_search);
                tab.setIcon(android.R.drawable.ic_menu_search);
            } else if (position == TAB_BOOKMARKS) {
                tab.setText(R.string.tab_bookmarks);
                tab.setIcon(android.R.drawable.star_big_off);
            } else {
                tab.setText(R.string.tab_recommendations);
                tab.setIcon(android.R.drawable.ic_menu_today);
            }
        }).attach();
        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
                handleTabReselected(tab.getPosition());
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (handleCurrentTabBack()) {
                    return;
                }
                long now = SystemClock.elapsedRealtime();
                if (now - lastBackPressedAt < 2000L) {
                    finish();
                    return;
                }
                lastBackPressedAt = now;
                Snackbar.make(binding.getRoot(), R.string.message_back_again_exit, Snackbar.LENGTH_SHORT).show();
            }
        });
        syncRecommendationSchedule();
        RecommendationNotificationHelper.ensureChannel(this);
        if (savedInstanceState == null) {
            applyStartTabIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyStartTabIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    private void syncRecommendationSchedule() {
        RecommendationSettingsStore settingsStore = new RecommendationSettingsStore(this);
        if (settingsStore.isEnabled()) {
            RecommendationScheduler.scheduleDaily(this, settingsStore.getHour(), settingsStore.getMinute());
            return;
        }
        RecommendationScheduler.cancelDaily(this);
    }

    private void applyStartTabIntent(Intent intent) {
        if (binding == null || intent == null) {
            return;
        }
        int startTab = intent.getIntExtra(EXTRA_START_TAB, TAB_TRENDING);
        if (startTab < TAB_TRENDING || startTab > TAB_RECOMMENDATIONS) {
            startTab = TAB_TRENDING;
        }
        binding.viewPager.setCurrentItem(startTab, false);
    }

    private void handleTabReselected(int position) {
        if (position != TAB_TRENDING) {
            return;
        }
        Fragment fragment = findTabFragment(position);
        if (fragment instanceof TrendingFragment) {
            ((TrendingFragment) fragment).handlePrimaryTabReselected();
        }
    }

    private boolean handleCurrentTabBack() {
        Fragment fragment = findTabFragment(binding.viewPager.getCurrentItem());
        if (fragment instanceof TrendingFragment) {
            return ((TrendingFragment) fragment).handleBackPressed();
        }
        return false;
    }

    private Fragment findTabFragment(int position) {
        return getSupportFragmentManager().findFragmentByTag("f" + position);
    }
}
