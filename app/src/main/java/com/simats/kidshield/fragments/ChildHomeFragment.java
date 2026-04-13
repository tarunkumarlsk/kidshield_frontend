package com.simats.kidshield.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.simats.kidshield.R;
import com.simats.kidshield.SOSActivity;
import com.simats.kidshield.models.ScreenUsageResponse;
import com.simats.kidshield.network.BackendManager;
import com.simats.kidshield.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;

public class ChildHomeFragment extends Fragment {

    private TextView tvUsedTime, tvRemainingTime, tvAiScore, tvAiStatus, welcomeText;
    private LinearProgressIndicator progressScreenTime;
    private int childId;
    
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadData();
            handler.postDelayed(this, 120000); // Child Home updates every 2 mins
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_child_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        childId = SessionManager.getInstance(requireContext()).getChildId();
        String childName = SessionManager.getInstance(requireContext()).getName();

        welcomeText = view.findViewById(R.id.welcome_text);
        if (childName != null) {
            welcomeText.setText("Welcome, " + childName.split(" ")[0] + "! 🎉");
        }

        tvUsedTime = view.findViewById(R.id.tv_used_time);
        tvRemainingTime = view.findViewById(R.id.tv_remaining_time);
        progressScreenTime = view.findViewById(R.id.progress_screen_time);
        tvAiScore = view.findViewById(R.id.tv_ai_score);
        tvAiStatus = view.findViewById(R.id.tv_ai_status);

        view.findViewById(R.id.btn_sos_fragment).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SOSActivity.class)));

        view.findViewById(R.id.card_my_apps_home).setOnClickListener(v -> {
            BottomNavigationView nav = requireActivity().findViewById(R.id.child_bottom_nav);
            if (nav != null) nav.setSelectedItemId(R.id.nav_apps_child);
        });

        view.findViewById(R.id.card_request_time).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), com.simats.kidshield.RequestTimeActivity.class));
        });

        loadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshRunnable);
        handler.post(refreshRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }

    private void loadData() {
        if (childId == -1) return;

        // Load Screen Usage and Limits
        BackendManager.getScreenUsage(childId, new BackendManager.ApiCallback<List<ScreenUsageResponse>>() {
            @Override
            public void onSuccess(List<ScreenUsageResponse> usageList) {
                if (!isAdded()) return;
                int totalMinutes = 0;
                for (ScreenUsageResponse u : usageList) {
                    totalMinutes += u.getUsageTimeMinutes();
                }
                
                final int finalTotalMinutes = totalMinutes;
                BackendManager.getScreenTimeLimits(childId, new BackendManager.ApiCallback<List<com.simats.kidshield.models.ScreenTimeLimitResponse>>() {
                    @Override
                    public void onSuccess(List<com.simats.kidshield.models.ScreenTimeLimitResponse> limits) {
                        if (!isAdded()) return;
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
                        calculateLocalAIScore(finalTotalMinutes, limit, usageList);
                    }

                    @Override
                    public void onError(String error) {
                        if (isAdded()) {
                            updateScreenTimeUI(finalTotalMinutes, 120, null);
                            calculateLocalAIScore(finalTotalMinutes, 120, usageList);
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (isAdded()) tvRemainingTime.setText("Usage unavailable");
            }
        });
    }

    private void calculateLocalAIScore(int totalMinutes, int limit, List<ScreenUsageResponse> usageList) {
        if (!isAdded()) return;
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
                if (!isAdded()) return;
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
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        tvAiScore.setText(String.valueOf(rScore));
                        tvAiStatus.setText(finalLabel);
                        try { tvAiScore.setTextColor(Color.parseColor(finalColor)); } catch (Exception e) {}
                    });
                }
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
        if (getContext() == null) return 0.0f;
        android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager) requireContext().getSystemService(android.content.Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 22);
        cal.set(java.util.Calendar.MINUTE, 0);
        long start = cal.getTimeInMillis();
        if (start > now) start -= 24 * 60 * 60 * 1000;
        long end = start + 8 * 60 * 60 * 1000;
        if (end > now) end = now;
        long totalTime = 0;
        java.util.List<android.app.usage.UsageStats> stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, end);
        if (stats != null) {
            for (android.app.usage.UsageStats usage : stats) totalTime += usage.getTotalTimeInForeground();
        }
        return (float) totalTime / (1000 * 60 * 60);
    }

    private void updateScreenTimeUI(int totalMinutes, int limit, com.simats.kidshield.models.ScreenTimeLimitResponse overallLimit) {
        if (!isAdded()) return;
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        tvUsedTime.setText(hours + "h " + mins + "m");
        int progress = (int) ((totalMinutes / (float) limit) * 100);
        progressScreenTime.setProgress(Math.min(progress, 100));
        int remaining = limit - totalMinutes;
        String statusText = (remaining > 0) ? remaining + "m remaining" : "Limit reached";
        if (remaining <= 0) tvRemainingTime.setTextColor(Color.RED);
        else tvRemainingTime.setTextColor(Color.parseColor("#9CA3AF"));

        if (overallLimit != null) {
            if (overallLimit.getBedtimeEnabled() && isTimeInRange(overallLimit.getBedtimeStart(), overallLimit.getBedtimeEnd())) {
                statusText += " (Bedtime active)";
                tvRemainingTime.setTextColor(Color.parseColor("#4F46E5"));
            } else if (overallLimit.getSchoolHoursEnabled() && isWeekday() && isTimeInRange(overallLimit.getSchoolStart(), overallLimit.getSchoolEnd())) {
                statusText += " (School Hours)";
                tvRemainingTime.setTextColor(Color.parseColor("#F97316"));
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
            return (startMins <= endMins) ? (currentMins >= startMins && currentMins <= endMins) : (currentMins >= startMins || currentMins <= endMins);
        } catch (Exception e) { return false; }
    }

    private boolean isWeekday() {
        int day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK);
        return day != java.util.Calendar.SATURDAY && day != java.util.Calendar.SUNDAY;
    }
}
