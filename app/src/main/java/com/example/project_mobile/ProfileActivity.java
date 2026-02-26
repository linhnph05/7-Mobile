package com.example.project_mobile;

import android.os.Bundle;
import android.widget.CompoundButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

public class ProfileActivity extends AppCompatActivity {

    private SwitchCompat switchDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Ánh xạ View
        switchDarkMode = findViewById(R.id.switchDarkMode);

        // 1. Kiểm tra trạng thái hiện tại của app đang là Sáng hay Tối
        int currentNightMode = AppCompatDelegate.getDefaultNightMode();

        // Cập nhật trạng thái của nút gạt cho khớp
        if (currentNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            switchDarkMode.setChecked(true);
        } else {
            switchDarkMode.setChecked(false);
            // Ép mặc định là Light Mode nếu chưa có thiết lập
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // 2. Bắt sự kiện khi người dùng gạt nút
        switchDarkMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Bật Dark Mode
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    // Tắt Dark Mode (về lại Light Mode)
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
            }
        });
    }
}