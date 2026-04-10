package com.in6222.litsync.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class TabPagerAdapter extends FragmentStateAdapter {

    public TabPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 1) {
            return new SearchFragment();
        }
        if (position == 2) {
            return new BookmarksFragment();
        }
        if (position == 3) {
            return new RecommendationsFragment();
        }
        return new TrendingFragment();
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}
