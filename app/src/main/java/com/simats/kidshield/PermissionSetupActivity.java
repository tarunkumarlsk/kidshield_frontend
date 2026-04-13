package com.simats.kidshield;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.simats.kidshield.utils.EdgeToEdgeUtils;
import com.simats.kidshield.utils.SessionManager;

/**
 * Guides the child user through granting the permissions KidShield needs
 * to enforce app blocking and screen-time limits.
 *
 * Steps:
 *  1. Usage Access  (PACKAGE_USAGE_STATS)
 *  2. Display Over Other Apps  (SYSTEM_ALERT_WINDOW)
 *  3. Notifications  (POST_NOTIFICATIONS – Android 13+)
 *
 * Once all required permissions are granted the activity navigates to
 * ChildMainActivity and sets a "permissions_granted" flag so that the
 * wizard is never shown again on this device.
 */
public class PermissionSetupActivity extends AppCompatActivity {

    // Steps: 1 = Usage Access, 2 = Overlay, 3 = Notifications (optional on < API 33)
    private int currentStep = 1;

    private TextView tvStepTitle, tvStepDesc, tvStepCounter;
    private ImageView ivStepIcon;
    private Button btnGrant, btnSkip;

    // Request codes for onActivityResult
    private static final int REQ_USAGE    = 101;
    private static final int REQ_OVERLAY  = 102;
    private static final int REQ_LOCATION = 104;
    private static final int REQ_NOTIF    = 103;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_setup);
        EdgeToEdgeUtils.applyTopPadding(findViewById(android.R.id.content));

        tvStepTitle   = findViewById(R.id.tv_permission_title);
        tvStepDesc    = findViewById(R.id.tv_permission_desc);
        tvStepCounter = findViewById(R.id.tv_step_counter);
        ivStepIcon    = findViewById(R.id.iv_permission_icon);
        btnGrant      = findViewById(R.id.btn_grant_permission);
        btnSkip       = findViewById(R.id.btn_skip_permission);

        btnGrant.setOnClickListener(v -> handleGrantClick());
        btnSkip.setOnClickListener(v -> advanceStep());

        // Start from the first un-granted step
        advanceToNextPendingStep();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-evaluate after user returns from Settings
        advanceToNextPendingStep();
    }

    // ── Step logic ────────────────────────────────────────────────────────────

    /** Moves currentStep to the first step that still needs granting. */
    private void advanceToNextPendingStep() {
        while (currentStep <= totalSteps()) {
            if (!isStepGranted(currentStep)) {
                showStep(currentStep);
                return;
            }
            currentStep++;
        }
        // All done — persist the flag and proceed
        finishPermissionSetup();
    }

    private int totalSteps() {
        int steps = 3; // Usage, Overlay, Location
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) steps++; // Notifications
        return steps;
    }

    private boolean isStepGranted(int step) {
        switch (step) {
            case 1: return hasUsageStatsPermission();
            case 2: return Settings.canDrawOverlays(this);
            case 3: return hasLocationPermission();
            case 4: return hasNotificationPermission();
            default: return true;
        }
    }

    private void showStep(int step) {
        tvStepCounter.setText("Step " + step + " of " + totalSteps());
        switch (step) {
            case 1:
                tvStepTitle.setText("Usage Access");
                tvStepDesc.setText(
                    "KidShield needs access to app usage information so it can track " +
                    "screen time and enforce daily limits set by your parent.\n\n" +
                    "Tap \"Grant Access\", then find KidShield in the list and enable it.");
                ivStepIcon.setImageResource(R.drawable.ic_shield);
                btnSkip.setText("Skip for now");
                btnGrant.setText("Grant Access");
                break;
            case 2:
                tvStepTitle.setText("Display Over Other Apps");
                tvStepDesc.setText(
                    "KidShield needs to display a blocking screen over apps that " +
                    "your parent has restricted.\n\n" +
                    "Tap \"Grant Access\", then enable \"Allow display over other apps\" " +
                    "for KidShield.");
                ivStepIcon.setImageResource(R.drawable.ic_shield);
                btnSkip.setText("Skip for now");
                btnGrant.setText("Grant Access");
                break;
            case 3:
                tvStepTitle.setText("Live Location");
                tvStepDesc.setText(
                    "KidShield needs your location to help your parent know you are safe " +
                    "and to send notifications when you arrive at or leave safe zones like home or school.\n\n" +
                    "Please allow \"Always\" or \"While using the app\" when prompted.");
                ivStepIcon.setImageResource(android.R.drawable.ic_menu_mylocation);
                btnSkip.setText("Skip for now");
                btnGrant.setText("Grant Location");
                break;
            case 4:
                tvStepTitle.setText("Notifications");
                tvStepDesc.setText(
                    "Allow KidShield to send you notifications about screen time " +
                    "limits, bedtime reminders, and important alerts.");
                ivStepIcon.setImageResource(R.drawable.ic_shield);
                btnSkip.setText("Skip");
                btnGrant.setText("Allow Notifications");
                break;
        }
    }

    private void handleGrantClick() {
        switch (currentStep) {
            case 1:
                showExplainerThenOpen(
                    "Enable Usage Access",
                    "On the next screen:\n1. Find \"KidShield\" in the list\n2. Tap it and turn on \"Permit usage access\"",
                    () -> startActivityForResult(
                        new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQ_USAGE)
                );
                break;
            case 2:
                showExplainerThenOpen(
                    "Enable Display Over Other Apps",
                    "On the next screen, find KidShield and enable \"Allow display over other apps\".",
                    () -> startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())), REQ_OVERLAY)
                );
                break;
            case 3:
                requestLocationPermission();
                break;
            case 4:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                }
                break;
        }
    }

    /** Advance past the current step (skip / already done path from onResume). */
    private void advanceStep() {
        currentStep++;
        advanceToNextPendingStep();
    }

    // ── Permission checks ─────────────────────────────────────────────────────

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true; 
    }

    private boolean hasLocationPermission() {
        boolean fine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean back = checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            return fine && back;
        }
        return fine;
    }

    private void requestLocationPermission() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                   checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
             new AlertDialog.Builder(this)
                .setTitle("Background Location")
                .setMessage("To keep the safety boundary active even when this app is closed, please select 'Allow all the time' on the next screen.")
                .setPositiveButton("Grant Always", (d, w) -> {
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_LOCATION);
                })
                .show();
        } else {
            advanceStep();
        }
    }

    // ── Finish ────────────────────────────────────────────────────────────────

    private void finishPermissionSetup() {
        SessionManager.getInstance(this).setPermissionsGranted(true);
        startActivity(new Intent(this, ChildMainActivity.class));
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Show a brief dialog explaining what the user should do in Settings. */
    private void showExplainerThenOpen(String title, String message, Runnable openSettings) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Open Settings", (d, w) -> openSettings.run())
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // onResume will re-evaluate and advance automatically
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            // Re-evaluate in advanceToNextPendingStep via onResume
        } else if (requestCode == REQ_NOTIF) {
            advanceStep();
        }
    }

    @Override
    public void onBackPressed() {
        // Block back press — permissions are required for app blocking to work
        new AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("KidShield needs these permissions to protect your device as set by your parent. Please complete the setup.")
            .setPositiveButton("Continue Setup", null)
            .show();
    }
}
