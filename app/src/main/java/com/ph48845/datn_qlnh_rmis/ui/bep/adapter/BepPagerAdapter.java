package com.ph48845.datn_qlnh_rmis.ui.bep;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Adapter cho ViewPager2 của BepActivity.
 */
public class BepPagerAdapter extends FragmentStateAdapter {

    private final Fragment[] fragments = new Fragment[2];

    public static final int INDEX_TABLES = 0;
    public static final int INDEX_SUMMARY = 1;

    public BepPagerAdapter(@NonNull FragmentActivity fa) {
        super(fa);
        fragments[INDEX_TABLES] = new BepTableFragment();
        fragments[INDEX_SUMMARY] = new BepSummaryFragment();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragments[position];
    }

    @Override
    public int getItemCount() {
        return fragments.length;
    }

    public Fragment getFragment(int index) {
        if (index < 0 || index >= fragments.length) return null;
        return fragments[index];
    }
}