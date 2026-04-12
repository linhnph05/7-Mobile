package com.team7.taskflow.ui.base;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import com.team7.taskflow.ui.profile.ProfileActivity;

/**
 * BaseActivity — tất cả Activity kế thừa class này sẽ tự động:
 * - Ẩn bàn phím khi nhấn ra ngoài vùng EditText
 * - Xóa focus khỏi EditText
 * - Áp dụng theme (Light/Dark) từ cài đặt
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Áp dụng theme lưu trong SharedPreferences TRƯỚC khi gọi super.onCreate()
        ProfileActivity.applySavedTheme(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View currentFocus = getCurrentFocus();
            if (currentFocus instanceof EditText) {
                int[] location = new int[2];
                currentFocus.getLocationOnScreen(location);
                float x = ev.getRawX();
                float y = ev.getRawY();
                // Kiểm tra touch có nằm ngoài EditText đang focus không
                if (x < location[0]
                        || x > location[0] + currentFocus.getWidth()
                        || y < location[1]
                        || y > location[1] + currentFocus.getHeight()) {
                    // Ẩn bàn phím
                    hideKeyboard(currentFocus);
                    // Xóa focus
                    currentFocus.clearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Ẩn bàn phím khỏi màn hình
     */
    protected void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}

