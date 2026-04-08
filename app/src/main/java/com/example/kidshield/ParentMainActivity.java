package com.example.kidshield;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.kidshield.fragments.DashboardFragment;
import com.example.kidshield.fragments.MapFragment;
import com.example.kidshield.fragments.AppControlFragment;
import com.example.kidshield.fragments.AlertsFragment;
import com.example.kidshield.fragments.ParentProfileFragment;
import com.example.kidshield.utils.EdgeToEdgeUtils;

public class ParentMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_main);
        EdgeToEdgeUtils.applyTopPadding(findViewById(R.id.parent_fragment_container));
        BottomNavigationView navView = findViewById(R.id.parent_bottom_nav);

        // Start Background Alert Monitoring
        try {
            android.content.Intent serviceIntent = new android.content.Intent(this, com.example.kidshield.services.ParentAlertService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            android.util.Log.e("ParentMain", "Could not start ParentAlertService: " + e.getMessage());
        }

        // Request Permissions for Notifications (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
        }

        navView.setOnItemSelectedListener(item -> {
            String tag;
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_home) {
                tag = "DASHBOARD";
                selectedFragment = getSupportFragmentManager().findFragmentByTag(tag);
                if (selectedFragment == null) selectedFragment = new DashboardFragment();
            } else if (itemId == R.id.nav_map) {
                tag = "MAP";
                selectedFragment = getSupportFragmentManager().findFragmentByTag(tag);
                if (selectedFragment == null) selectedFragment = new MapFragment();
            } else if (itemId == R.id.nav_controls) {
                tag = "CONTROLS";
                selectedFragment = getSupportFragmentManager().findFragmentByTag(tag);
                if (selectedFragment == null) selectedFragment = new AppControlFragment();
            } else if (itemId == R.id.nav_alerts) {
                tag = "ALERTS";
                selectedFragment = getSupportFragmentManager().findFragmentByTag(tag);
                if (selectedFragment == null) selectedFragment = new AlertsFragment();
            } else if (itemId == R.id.nav_settings) {
                tag = "PROFILE";
                selectedFragment = getSupportFragmentManager().findFragmentByTag(tag);
                if (selectedFragment == null) selectedFragment = new ParentProfileFragment();
            } else {
                return false;
            }

            getSupportFragmentManager().beginTransaction()
                .replace(R.id.parent_fragment_container, selectedFragment, tag)
                .commit();
            return true;
        });

        // Set default fragment
        if (savedInstanceState == null) {
            navView.setSelectedItemId(R.id.nav_home);
        }
    }

    @Override
    public void onBackPressed() {
        BottomNavigationView navView = findViewById(R.id.parent_bottom_nav);
        if (navView.getSelectedItemId() != R.id.nav_home) {
            navView.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }
}
