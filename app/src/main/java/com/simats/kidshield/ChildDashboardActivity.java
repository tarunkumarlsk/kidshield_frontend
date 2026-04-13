package com.simats.kidshield;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.simats.kidshield.utils.EdgeToEdgeUtils;
import com.simats.kidshield.models.ScreenUsageResponse;
import com.simats.kidshield.network.BackendManager;
import com.simats.kidshield.utils.SessionManager;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.ArrayList;
import java.util.List;

public class ChildDashboardActivity extends AppCompatActivity {

    private TextView tvWelcome, tvUsedTime, tvRemainingTime, tvAiStatus, tvAiScore;
    private LinearProgressIndicator progressScreenTime;
    private int childId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_dashboard);
        EdgeToEdgeUtils.applyTopPadding(findViewById(android.R.id.content));

        childId = SessionManager.getInstance(this).getChildId();
        String childName = SessionManager.getInstance(this).getName();

        tvWelcome = findViewById(R.id.welcome_text);
        tvUsedTime = findViewById(R.id.tv_used_time);
        tvRemainingTime = findViewById(R.id.tv_remaining_time);
        tvAiStatus = findViewById(R.id.tv_ai_status);
        tvAiScore = findViewById(R.id.tv_ai_score);
        progressScreenTime = findViewById(R.id.progress_screen_time);

        if (childName != null && !childName.trim().isEmpty()) {
            String[] parts = childName.trim().split(" ");
            if (parts.length > 0) {
                tvWelcome.setText("Welcome, " + parts[0] + "! 🎉");
            }
        }

        setupClickListeners();
        verifyChildStatus();
        loadData();
        syncInstalledApps();
        
        // Request required runtime permissions before starting services
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            }, 100);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            }, 100);
        }

        if (!hasUsageStatsPermission(this)) {
            Toast.makeText(this, "Usage Access is required to protect your device.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }

        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow KidShield to 'Display over other apps'", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }

        startBackgroundServices();
    }

    private boolean hasUsageStatsPermission(android.content.Context context) {
        android.app.AppOpsManager appOps = (android.app.AppOpsManager) context.getSystemService(android.content.Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
            android.os.Process.myUid(), context.getPackageName());
        return mode == android.app.AppOpsManager.MODE_ALLOWED;
    }

    private void verifyChildStatus() {
        if (childId == -1) return;
        BackendManager.checkChildStatus(childId, new BackendManager.ApiCallback<java.util.Map<String, Object>>() {
            @Override
            public void onSuccess(java.util.Map<String, Object> result) {
                Boolean linked = (Boolean) result.get("linked");
                if (linked != null && !linked) {
                    Toast.makeText(ChildDashboardActivity.this, "Device link has been removed by parent. Please re-setup.", Toast.LENGTH_LONG).show();
                    SessionManager.getInstance(ChildDashboardActivity.this).clearSession();
                    startActivity(new Intent(ChildDashboardActivity.this, RoleActivity.class));
                    finish();
                }
            }

            @Override
            public void onError(String error) {
                // If it's a 404, the child doesn't exist anymore
                if (error != null && error.contains("404")) {
                    Toast.makeText(ChildDashboardActivity.this, "Session expired or child removed. Please re-link.", Toast.LENGTH_LONG).show();
                    SessionManager.getInstance(ChildDashboardActivity.this).clearSession();
                    startActivity(new Intent(ChildDashboardActivity.this, ChildLoginActivity.class));
                    finish();
                }
            }
        });
    }

    private void startBackgroundServices() {
        try {
            startService(new Intent(this, com.simats.kidshield.services.LocationService.class));
        } catch (Exception e) {
            android.util.Log.e("ChildDashboard", "Could not start LocationService: " + e.getMessage());
        }
        
        try {
            startService(new Intent(this, com.simats.kidshield.services.ScreenTimeService.class));
        } catch (Exception e) {
            android.util.Log.e("ChildDashboard", "Could not start ScreenTimeService: " + e.getMessage());
        }

        try {
            startService(new Intent(this, com.simats.kidshield.services.AppBlockerService.class));
        } catch (Exception e) {
            android.util.Log.e("ChildDashboard", "Could not start AppBlockerService: " + e.getMessage());
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.btn_sos).setOnClickListener(v -> {
            startActivity(new Intent(this, SOSActivity.class));
        });

        findViewById(R.id.card_request_time).setOnClickListener(v -> {
            startActivity(new Intent(this, RequestTimeActivity.class));
        });

        findViewById(R.id.card_my_apps).setOnClickListener(v -> {
            startActivity(new Intent(this, MyAppsActivity.class));
        });



        // Bottom Navigation (Custom)
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            // Already here
        });

        findViewById(R.id.nav_apps).setOnClickListener(v -> {
            startActivity(new Intent(this, MyAppsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            startActivity(new Intent(this, ChildProfileActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void loadData() {
        if (childId == -1) return;

        // Load Screen Usage and Limits
        BackendManager.getScreenUsage(childId, new BackendManager.ApiCallback<List<ScreenUsageResponse>>() {
            @Override
            public void onSuccess(List<ScreenUsageResponse> usageList) {
                int totalMinutes = 0;
                for (ScreenUsageResponse u : usageList) {
                    totalMinutes += u.getUsageTimeMinutes();
                }
                
                final int finalTotalMinutes = totalMinutes;
                BackendManager.getScreenTimeLimits(childId, new BackendManager.ApiCallback<List<com.simats.kidshield.models.ScreenTimeLimitResponse>>() {
                    @Override
                    public void onSuccess(List<com.simats.kidshield.models.ScreenTimeLimitResponse> limits) {
                        int limit = 120;
                        com.simats.kidshield.models.ScreenTimeLimitResponse overallLimit = null;
                        
                        for (com.simats.kidshield.models.ScreenTimeLimitResponse l : limits) {
                            if (l.getAppName() == null || l.getAppName().isEmpty()) {
                                overallLimit = l;
                                limit = l.getLimitMinutes();
                                break;
                            }
                        }
                        updateScreenTimeUI(finalTotalMinutes, limit, overallLimit);
                        
                        // Now calculate AI Score locally
                        calculateLocalAIScore(finalTotalMinutes, limit, usageList);
                    }

                    @Override
                    public void onError(String error) {
                        updateScreenTimeUI(finalTotalMinutes, 120, null);
                        calculateLocalAIScore(finalTotalMinutes, 120, usageList);
                    }
                });
            }

            public void onError(String error) {
                tvRemainingTime.setText("Usage unavailable");
            }
        });
    }

    private void calculateLocalAIScore(int totalMinutes, int limit, List<ScreenUsageResponse> usageList) {
        float screenTimeRatio = (float) totalMinutes / Math.max(1, limit);
        
        int socialMins = 0, eduMins = 0;
        for (ScreenUsageResponse u : usageList) {
            String pkg = u.getAppName().toLowerCase();
            if (isSocialApp(pkg)) socialMins += u.getUsageTimeMinutes();
            else if (isEduApp(pkg)) eduMins += u.getUsageTimeMinutes();
        }
        float socialMediaFrac = totalMinutes > 0 ? (float) socialMins / totalMinutes : 0;
        float educationalFrac = totalMinutes > 0 ? (float) eduMins / totalMinutes : 0;
        float lateNightUsageHrs = getLateNightUsageHours();
        
        BackendManager.getAlerts(childId, new BackendManager.ApiCallback<List<com.simats.kidshield.models.AlertResponse>>() {
            @Override
            public void onSuccess(List<com.simats.kidshield.models.AlertResponse> alerts) {
                int geofenceExits = 0;
                int sosCount7d = 0;
                long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
                
                for (com.simats.kidshield.models.AlertResponse a : alerts) {
                    if ("geofence".equalsIgnoreCase(a.getAlertType())) geofenceExits++;
                    if ("sos".equalsIgnoreCase(a.getAlertType())) {
                        try {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
                            java.util.Date d = sdf.parse(a.getCreatedAt());
                            if (d != null && d.getTime() > sevenDaysAgo) sosCount7d++;
                        } catch (Exception e) {}
                    }
                }
                
                float score = 100.0f;
                if (screenTimeRatio > 1.0f) score -= (screenTimeRatio - 1.0f) * 15.0f;
                score -= (lateNightUsageHrs * 5.0f);
                if (socialMediaFrac > 0.4f) score -= ((socialMediaFrac - 0.4f) * 20.0f);
                if (educationalFrac < 0.1f) score -= 5.0f;
                else score += (educationalFrac * 5.0f);
                score -= (geofenceExits * 4.0f);
                score -= (sosCount7d * 10.0f);
                
                score = Math.max(0.0f, Math.min(100.0f, score));
                int rScore = Math.round(score);
                
                String label = "Unknown";
                String color = "#94A3B8";
                if (rScore >= 90)      { label = "Safe 🟢";     color = "#10B981"; }
                else if (rScore >= 70) { label = "Moderate 🟡"; color = "#F59E0B"; }
                else if (rScore >= 50) { label = "Warning 🟠";  color = "#F97316"; }
                else                   { label = "Danger 🔴";   color = "#EF4444"; }
                
                final String finalLabel = label;
                final String finalColor = color;
                
                runOnUiThread(() -> {
                    tvAiScore.setText(String.valueOf(rScore));
                    tvAiStatus.setText(finalLabel);
                    try { tvAiScore.setTextColor(Color.parseColor(finalColor)); } catch (Exception e) {}
                });
            }
            @Override
            public void onError(String error) { }
        });
    }

    private boolean isSocialApp(String pkg) {
        return pkg.contains("facebook") || pkg.contains("instagram") || pkg.contains("tiktok") || 
               pkg.contains("snapchat") || pkg.contains("youtube") || pkg.contains("twitter") || 
               pkg.contains("whatsapp") || pkg.contains("messenger") || pkg.contains("reddit");
    }

    private boolean isEduApp(String pkg) {
        return pkg.contains("duolingo") || pkg.contains("classroom") || pkg.contains("khan") || 
               pkg.contains("scholar") || pkg.contains("learning") || pkg.contains("dictionary") || 
               pkg.contains("wikipedia") || pkg.contains("math") || pkg.contains("science");
    }

    private float getLateNightUsageHours() {
        if (!hasUsageStatsPermission(this)) return 0.0f;
        
        android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager) getSystemService(android.content.Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        
        // Check usage in last 12 hours specifically between 10PM and 6AM
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 22);
        cal.set(java.util.Calendar.MINUTE, 0);
        long start = cal.getTimeInMillis();
        if (start > now) start -= 24 * 60 * 60 * 1000; // Previous night if current is before 10PM
        
        long end = start + 8 * 60 * 60 * 1000; // 6 AM
        if (end > now) end = now;

        long totalTime = 0;
        java.util.List<android.app.usage.UsageStats> stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, end);
        if (stats != null) {
            for (android.app.usage.UsageStats usage : stats) {
                totalTime += usage.getTotalTimeInForeground();
            }
        }
        return (float) totalTime / (1000 * 60 * 60); // Convert to hours
    }

    private void syncInstalledApps() {
        if (childId == -1) return;
        new Thread(() -> {
            List<String> apps = getInstalledAppsList();
            BackendManager.syncAppInventory(childId, apps, new BackendManager.ApiCallback<String>() {
                @Override public void onSuccess(String result) {}
                @Override public void onError(String error) {
                    android.util.Log.e("ChildDashboard", "App sync error: " + error);
                }
            });
        }).start();
    }

    private List<String> getInstalledAppsList() {
        List<String> appNames = new ArrayList<>();
        android.content.pm.PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);

        for (android.content.pm.ApplicationInfo app : apps) {
            // Filter out system apps common on emulators to keep it clean, or keep all
            if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                String name = (String) pm.getApplicationLabel(app);
                if (name != null) appNames.add(name);
            }
        }
        // Always include common ones for demo purposes if list is empty
        if (appNames.isEmpty()) {
            appNames.add("YouTube");
            appNames.add("Instagram");
            appNames.add("Chrome");
        }
        return appNames;
    }

    private void updateScreenTimeUI(int totalMinutes, int limit, com.simats.kidshield.models.ScreenTimeLimitResponse overallLimit) {
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        tvUsedTime.setText(hours + "h " + mins + "m");
        
        int progress = (int) ((totalMinutes / (float) limit) * 100);
        progressScreenTime.setProgress(Math.min(progress, 100));
        
        int remaining = limit - totalMinutes;
        String statusText = "";
        if (remaining > 0) {
            statusText = remaining + "m remaining";
            tvRemainingTime.setTextColor(Color.parseColor("#9CA3AF"));
        } else {
            statusText = "Limit reached";
            tvRemainingTime.setTextColor(Color.RED);
        }

        if (overallLimit != null) {
            if (overallLimit.getBedtimeEnabled() && overallLimit.getBedtimeStart() != null && overallLimit.getBedtimeEnd() != null) {
                if (isTimeInRange(overallLimit.getBedtimeStart(), overallLimit.getBedtimeEnd())) {
                    statusText += " (Bedtime active)";
                    tvRemainingTime.setTextColor(Color.parseColor("#4F46E5"));
                }
            } else if (overallLimit.getSchoolHoursEnabled() && overallLimit.getSchoolStart() != null && overallLimit.getSchoolEnd() != null) {
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                int day = calendar.get(java.util.Calendar.DAY_OF_WEEK);
                boolean isWeekday = day != java.util.Calendar.SATURDAY && day != java.util.Calendar.SUNDAY;
                if (isWeekday && isTimeInRange(overallLimit.getSchoolStart(), overallLimit.getSchoolEnd())) {
                    statusText += " (School Hours)";
                    tvRemainingTime.setTextColor(Color.parseColor("#F97316"));
                }
            }
        }
        
        tvRemainingTime.setText(statusText);
    }

    private boolean isTimeInRange(String startTime, String endTime) {
        try {
            java.util.Calendar now = java.util.Calendar.getInstance();
            int currentMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);

            String[] s = startTime.split(":");
            int startMins = Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]);

            String[] e = endTime.split(":");
            int endMins = Integer.parseInt(e[0]) * 60 + Integer.parseInt(e[1]);

            if (startMins <= endMins) {
                return currentMins >= startMins && currentMins <= endMins;
            } else {
                return currentMins >= startMins || currentMins <= endMins;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
