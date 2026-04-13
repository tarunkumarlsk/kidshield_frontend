package com.simats.kidshield;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.simats.kidshield.network.BackendManager;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail, etOtp, etNewPassword;
    private Button btnSend;
    private LinearLayout otpContainer;
    private TextView title, subtitle;
    private int currentStep = 1; // 1 = Request, 2 = Reset

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        etEmail = findViewById(R.id.et_email);
        etOtp = findViewById(R.id.et_otp);
        etNewPassword = findViewById(R.id.et_new_password);
        btnSend = findViewById(R.id.btn_send);
        otpContainer = findViewById(R.id.otp_reset_container);
        title = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        
        ImageView btnBack = findViewById(R.id.btn_back);
        TextView btnLogin = findViewById(R.id.btn_login);

        btnBack.setOnClickListener(v -> finish());
        btnLogin.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> {
            if (currentStep == 1) {
                handleRequestOTP();
            } else {
                handleResetPassword();
            }
        });
    }

    private void handleRequestOTP() {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSend.setEnabled(false);
        btnSend.setText("Sending...");

        BackendManager.requestResetOTP(email, new BackendManager.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                btnSend.setEnabled(true);
                btnSend.setText("Verify & Reset");
                currentStep = 2;
                
                // Show OTP fields, update UI
                otpContainer.setVisibility(View.VISIBLE);
                title.setText("Reset Password");
                subtitle.setText("Enter the OTP sent to " + email);
                etEmail.setEnabled(false); // Lock email
                
                Toast.makeText(ForgotPasswordActivity.this, result, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                btnSend.setEnabled(true);
                btnSend.setText("Send Reset Link");
                Toast.makeText(ForgotPasswordActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleResetPassword() {
        String email = etEmail.getText().toString().trim();
        String otp = etOtp.getText().toString().trim();
        String newPass = etNewPassword.getText().toString().trim();

        if (otp.length() < 6 || newPass.isEmpty()) {
            Toast.makeText(this, "Fill in OTP and New Password", Toast.LENGTH_SHORT).show();
            return;
        }

        // Password Validation: 8 chars, 1 upper, 1 lower, 1 digit, 1 symbol
        if (newPass.length() < 8) {
            Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPass.matches(".*[A-Z].*")) {
            Toast.makeText(this, "Password must include at least one uppercase letter", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPass.matches(".*[a-z].*")) {
            Toast.makeText(this, "Password must include at least one lowercase letter", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPass.matches(".*[0-9].*")) {
            Toast.makeText(this, "Password must include at least one number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPass.matches(".*[^A-Za-z0-9].*")) {
            Toast.makeText(this, "Password must include at least one symbol (e.g. @, #, !)", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSend.setEnabled(false);
        btnSend.setText("Resetting...");

        BackendManager.resetPassword(email, otp, newPass, new BackendManager.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                Toast.makeText(ForgotPasswordActivity.this, result, Toast.LENGTH_LONG).show();
                finish(); // Success, go back to login
            }

            @Override
            public void onError(String error) {
                btnSend.setEnabled(true);
                btnSend.setText("Verify & Reset");
                Toast.makeText(ForgotPasswordActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
