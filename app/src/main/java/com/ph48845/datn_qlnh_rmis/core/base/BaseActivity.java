package com.ph48845.datn_qlnh_rmis.core.base;

import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewbinding.ViewBinding;

/**
 * Generic BaseActivity to standardize setup across Activities.
 * Usage: public class MyActivity extends BaseActivity<ActivityMyBinding> { ... }
 */
public abstract class BaseActivity<T extends ViewBinding> extends AppCompatActivity {

    protected T binding;

    /**
     * Implement to inflate specific binding:
     * return ActivityMyBinding.inflate(inflater);
     */
    protected abstract T initBinding(LayoutInflater inflater);

    //test git

    /**
     * Called after binding is set. Override to init views.
     */
    protected void setupViews() {}

    /**
     * Called after setupViews. Override to observe ViewModel LiveData.
     */
    protected void setupObservers() {}

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = initBinding(getLayoutInflater());
        setContentView(binding.getRoot());
        setupViews();
        setupObservers();
    }
}
