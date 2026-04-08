package com.example.kidshield.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.kidshield.R;
import com.example.kidshield.RoleActivity;
import com.example.kidshield.utils.SessionManager;
import com.example.kidshield.network.BackendManager;
import com.example.kidshield.models.ScreenUsageResponse;
import com.example.kidshield.models.ScreenTimeLimitResponse;
import com.example.kidshield.models.AlertResponse;
import java.util.List;

public class ChildProfileFragment extends Fragment {

    private TextView tvName, tvDevice;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_child_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvName = view.findViewById(R.id.tv_child_name);
        tvDevice = view.findViewById(R.id.tv_device_info);

        SessionManager session = SessionManager.getInstance(requireContext());
        String name = session.getName();
        String device = session.getDeviceName();

        tvName.setText(name);
        tvDevice.setText(device);

        // UI elements for AI Score and Usage
        TextView tvScore = view.findViewById(R.id.tv_ai_score);
        TextView tvStatus = view.findViewById(R.id.tv_ai_status);
        View dot = view.findViewById(R.id.view_ai_status_dot);
        TextView tvTotalUsed = view.findViewById(R.id.tv_total_used);
        TextView tvUsedLimit = view.findViewById(R.id.tv_used_limit);
        com.google.android.material.progressindicator.LinearProgressIndicator progressUsage = view.findViewById(R.id.progress_usage);

        int childId = session.getChildId();
        if (childId != -1) {
            BackendManager.getScreenUsage(childId, new BackendManager.ApiCallback<List<ScreenUsageResponse>>() {
                @Override
                public void onSuccess(List<ScreenUsageResponse> usageList) {
                    BackendManager.getScreenTimeLimits(childId, new BackendManager.ApiCallback<List<ScreenTimeLimitResponse>>() {
                        @Override
                        public void onSuccess(List<ScreenTimeLimitResponse> limits) {
                            int limit = 120;
                            for (ScreenTimeLimitResponse l : limits) {
                                if (l.getAppName() == null || l.getAppName().isEmpty()) limit = l.getLimitMinutes();
                            }
                            
                            int totalMins = 0, socialMins = 0, eduMins = 0;
                            for (ScreenUsageResponse u : usageList) {
                                int mins = u.getUsageTimeMinutes();
                                totalMins += mins;
                                String pkg = (u.getAppName() != null ? u.getAppName() : "").toLowerCase();
                                if (isSocial(pkg)) socialMins += mins;
                                else if (isEdu(pkg)) eduMins += mins;
                            }
                            
                            final int finalLimit = limit;
                            final int finalTotalMins = totalMins;
                            final int finalSocialMins = socialMins;
                            final int finalEduMins = eduMins;
                            
                            BackendManager.getAlerts(childId, new BackendManager.ApiCallback<List<AlertResponse>>() {
                                @Override
                                public void onSuccess(List<AlertResponse> alerts) {
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(() -> {
                                            if (!isAdded()) return;
                                            calculateAndShow(alerts, finalTotalMins, finalLimit, finalSocialMins, finalEduMins, 
                                                tvScore, tvStatus, dot, tvTotalUsed, tvUsedLimit, progressUsage);
                                        });
                                    }
                                }
                                @Override public void onError(String e) {}
                            });
                        }
                        @Override public void onError(String e) {}
                    });
                }
                @Override public void onError(String e) {}
            });
        }

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            SessionManager.getInstance(requireContext()).clearSession();
            Intent intent = new Intent(requireContext(), RoleActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private boolean isSocial(String pkg) {
        return pkg.contains("facebook") || pkg.contains("instagram") || pkg.contains("tiktok") || 
               pkg.contains("snapchat") || pkg.contains("youtube") || pkg.contains("twitter") || 
               pkg.contains("whatsapp") || pkg.contains("messenger") || pkg.contains("reddit");
    }

    private boolean isEdu(String pkg) {
        return pkg.contains("duolingo") || pkg.contains("classroom") || pkg.contains("khan") || 
               pkg.contains("scholar") || pkg.contains("learning") || pkg.contains("dictionary") || 
               pkg.contains("wikipedia") || pkg.contains("math") || pkg.contains("science");
    }

    private void calculateAndShow(List<AlertResponse> alerts, int totalMins, int limit, int socialMins, int eduMins,
                                TextView tvScore, TextView tvStatus, View dot, 
                                TextView tvTotalUsed, TextView tvUsedLimit, 
                                com.google.android.material.progressindicator.LinearProgressIndicator progressUsage) {
        
        int geofenceExits = 0;
        int sosCount7d = 0;
        long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        
        for (AlertResponse a : alerts) {
            if ("geofence".equalsIgnoreCase(a.getAlertType())) geofenceExits++;
            if ("sos".equalsIgnoreCase(a.getAlertType())) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
                    java.util.Date d = sdf.parse(a.getCreatedAt());
                    if (d != null && d.getTime() > sevenDaysAgo) sosCount7d++;
                } catch (Exception ignored) {}
            }
        }
        
        float score = 100.0f;
        float screenTimeRatio = (float) totalMins / Math.max(1, limit);
        float socialMediaFrac = totalMins > 0 ? (float) socialMins / totalMins : 0;
        float educationalFrac = totalMins > 0 ? (float) eduMins / totalMins : 0;
        
        if (screenTimeRatio > 1.0f) score -= (screenTimeRatio - 1.0f) * 15.0f;
        
        int nowH = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if ((nowH >= 22 || nowH < 6) && totalMins > 30) score -= ((float) totalMins / 60.0f) * 5.0f;

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

        tvScore.setText(String.valueOf(rScore));
        try { tvScore.setTextColor(android.graphics.Color.parseColor(color)); } catch(Exception ignored){}
        tvStatus.setText("Status: " + label);
        dot.getBackground().mutate();
        try { dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))); } catch(Exception ignored){}

        // Update Usage UI
        tvTotalUsed.setText((totalMins / 60) + "h " + (totalMins % 60) + "m");
        tvUsedLimit.setText("Limit: " + (limit / 60) + "h " + (limit % 60) + "m");
        int pct = (int)((float)totalMins / limit * 100);
        progressUsage.setProgress(Math.min(pct, 100));
        if (pct >= 90) progressUsage.setIndicatorColor(android.graphics.Color.RED);
    }
}
