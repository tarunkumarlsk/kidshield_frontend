package com.simats.kidshield;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.simats.kidshield.fragments.MyAppsFragment;
import com.simats.kidshield.fragments.ChildProfileFragment;
import com.simats.kidshield.fragments.ChildHomeFragment;
import com.simats.kidshield.utils.EdgeToEdgeUtils;

public class ChildMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_main);
        EdgeToEdgeUtils.applyTopPadding(findViewById(android.R.id.content));

        BottomNavigationView navView = findViewById(R.id.child_bottom_nav);
        navView.setOnItemSelectedListener(item -> {
            String tag;
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_home_child) {
                tag = "HOME";
                selectedFragment = getSupportFragmentManager().findFragmentByTag(tag);
                if (selectedFragment == null) selectedFragment = new ChildHomeFragment();
            } else if (itemId == R.id.nav_apps_child) {
                tag = "APPS";
                selectedFragment = getSupportFragmentManager().findFragmentByTag(tag);
                if (selectedFragment == null) selectedFragment = new MyAppsFragment();
            } else if (itemId == R.id.nav_profile_child) {
                tag = "PROFILE";
                selectedFragment = getSupportFragmentManager().findFragmentByTag(tag);
                if (selectedFragment == null) selectedFragment = new ChildProfileFragment();
            } else {
                return false;
            }

            getSupportFragmentManager().beginTransaction()
                .replace(R.id.child_fragment_container, selectedFragment, tag)
                .commit();
            return true;
        });

        if (savedInstanceState == null) {
            navView.setSelectedItemId(R.id.nav_home_child);
        }

        findViewById(R.id.btn_sos).setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, SOSActivity.class));
        });

        startBackgroundServices();
    }

    @Override
    public void onBackPressed() {
        BottomNavigationView navView = findViewById(R.id.child_bottom_nav);
        if (navView.getSelectedItemId() != R.id.nav_home_child) {
            // If not on Home, go back to Home before exiting
            navView.setSelectedItemId(R.id.nav_home_child);
        } else {
            super.onBackPressed();
        }
    }

    private void startBackgroundServices() {
        try {
            startService(new android.content.Intent(this, com.simats.kidshield.services.LocationService.class));
        } catch (Exception e) {
            android.util.Log.e("ChildMain", "Could not start LocationService: " + e.getMessage());
        }
        
        try {
            startService(new android.content.Intent(this, com.simats.kidshield.services.ScreenTimeService.class));
        } catch (Exception e) {
            android.util.Log.e("ChildMain", "Could not start ScreenTimeService: " + e.getMessage());
        }

        try {
            startService(new android.content.Intent(this, com.simats.kidshield.services.AppBlockerService.class));
        } catch (Exception e) {
            android.util.Log.e("ChildMain", "Could not start AppBlockerService: " + e.getMessage());
        }
    }
}
