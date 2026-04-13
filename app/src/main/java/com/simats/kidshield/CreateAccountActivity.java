package com.simats.kidshield;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.simats.kidshield.utils.EdgeToEdgeUtils;

public class CreateAccountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);
        EdgeToEdgeUtils.applyTopPadding(findViewById(android.R.id.content));

        ImageView btnBack = findViewById(R.id.btn_back);
        TextView btnLogin = findViewById(R.id.btn_login);
        android.widget.Button btnCreate = findViewById(R.id.btn_create);

        btnBack.setOnClickListener(v -> finish());
        btnLogin.setOnClickListener(v -> finish());

        btnCreate.setOnClickListener(v -> {
            android.widget.EditText etName = findViewById(R.id.et_name);
            android.widget.EditText etEmail = findViewById(R.id.et_email);
            android.widget.EditText etPhone = findViewById(R.id.et_phone);
            android.widget.EditText etPassword = findViewById(R.id.et_password);
            android.widget.EditText etConfirm = findViewById(R.id.et_confirm_password);
            android.widget.CheckBox cbTerms = findViewById(R.id.cb_terms);

            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String password = etPassword.getText().toString();
            String confirm = etConfirm.getText().toString();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Name, Email and Password are required", Toast.LENGTH_SHORT).show();
                return;
            }

            // Name Validation: Only alphabets and spaces, length >= 2
            if (!name.matches("^[A-Za-z ]+$") || name.replace(" ", "").length() < 2) {
                Toast.makeText(this, "Name must contain only alphabets and be valid", Toast.LENGTH_SHORT).show();
                return;
            }

            // Phone Validation: Starts with 6-9, 10 digits
            if (!phone.matches("^[6-9][0-9]{9}$")) {
                Toast.makeText(this, "Phone number must start with 6-9 and contain 10 digits", Toast.LENGTH_LONG).show();
                return;
            }

            // Email Validation: Format and specific allowed domains
            String[] allowedDomains = {
                "gmail.com", "yahoo.com", "saveetha.com", "outlook.com"
            };
            boolean isValidDomain = false;
            String lowerEmail = email.toLowerCase();
            if (lowerEmail.contains("@")) {
                String domain = lowerEmail.split("@")[1];
                for (String d : allowedDomains) {
                    if (d.equals(domain)) {
                        isValidDomain = true;
                        break;
                    }
                }
            }
            if (!isValidDomain) {
                Toast.makeText(this, "Use a valid email provider (e.g. @gmail.com, @saveetha.com, etc)", Toast.LENGTH_LONG).show();
                return;
            }

            // Password Validation: 8 chars, 1 upper, 1 lower, 1 digit, 1 symbol
            if (password.length() < 8) {
                Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.matches(".*[A-Z].*")) {
                Toast.makeText(this, "Password must include at least one uppercase letter", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.matches(".*[a-z].*")) {
                Toast.makeText(this, "Password must include at least one lowercase letter", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.matches(".*[0-9].*")) {
                Toast.makeText(this, "Password must include at least one number", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.matches(".*[^A-Za-z0-9].*")) {
                Toast.makeText(this, "Password must include at least one symbol (e.g. @, #, !)", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!cbTerms.isChecked()) {
                Toast.makeText(this, "Please agree to the Terms of Service", Toast.LENGTH_SHORT).show();
                return;
            }

            btnCreate.setEnabled(false);
            btnCreate.setText("Creating...");

            com.simats.kidshield.network.BackendManager.registerParent(email, password, name, phone,
                    new com.simats.kidshield.network.BackendManager.ApiCallback<String>() {
                        @Override
                        public void onSuccess(String result) {
                            Toast.makeText(CreateAccountActivity.this, "Account created! You can now log in.",
                                    Toast.LENGTH_LONG).show();
                            finish();
                        }

                        @Override
                        public void onError(String error) {
                            btnCreate.setEnabled(true);
                            btnCreate.setText("Create Account");
                            Toast.makeText(CreateAccountActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}
