package com.simats.kidshield;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SubscriptionActivity extends AppCompatActivity {

    private String selectedPlan = "monthly";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscription);


        LinearLayout planMonthly = findViewById(R.id.planMonthly);
        LinearLayout planYearly = findViewById(R.id.planYearly);
        Button btnSubscribe = findViewById(R.id.btnSubscribe);

        // Select Monthly
        planMonthly.setOnClickListener(v -> {
            selectedPlan = "monthly";
            Toast.makeText(this, "Monthly Plan Selected", Toast.LENGTH_SHORT).show();
        });

        // Select Yearly
        planYearly.setOnClickListener(v -> {
            selectedPlan = "yearly";
            Toast.makeText(this, "Yearly Plan Selected", Toast.LENGTH_SHORT).show();
        });

        // Subscribe button
        btnSubscribe.setOnClickListener(v -> {
            saveSubscription(selectedPlan);

            Toast.makeText(this, "Subscribed to " + selectedPlan, Toast.LENGTH_LONG).show();

            // Go to SplashActivity
            Intent intent = new Intent(SubscriptionActivity.this, SplashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void saveSubscription(String plan) {
        SharedPreferences prefs = getSharedPreferences("kidshield_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("subscription_plan", plan);
        editor.putBoolean("is_subscribed", true);
        editor.apply();
    }
}