package com.team7.taskflow.ui.ai;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.team7.taskflow.R;

public class AiCreateActivity extends AppCompatActivity {

    private View bottomSheet;
    private ImageButton btnSaveTask;
    private View bgOverlay;
    private EditText etPrompt, etParsedTitle;
    
    // Cards & Texts phục hồi tủ đủ 5 thẻ
    private CardView cardStartDate, cardDueDate, cardPriority, cardAssignee, cardTag;
    private TextView tvParsedStartDate, tvParsedDueDate, tvParsedPriority, tvParsedAssignee, tvParsedTag;
    private ImageView ivParsedStartDate, ivParsedDueDate, ivParsedAssignee, ivParsedTag;

    // NLP Auto-parsing
    private Handler parseHandler = new Handler(Looper.getMainLooper());
    private Runnable parseRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setContentView(R.layout.activity_ai_create);

        // Bind root layout
        bottomSheet = findViewById(R.id.bottomSheet);
        bgOverlay = findViewById(R.id.bgOverlay);
        btnSaveTask = findViewById(R.id.btnSaveTask);
        etPrompt = findViewById(R.id.etPrompt);
        etParsedTitle = findViewById(R.id.etParsedTitle);

        // Bind chips
        cardStartDate = findViewById(R.id.cardStartDate);
        cardDueDate = findViewById(R.id.cardDueDate);
        cardPriority = findViewById(R.id.cardPriority);
        cardAssignee = findViewById(R.id.cardAssignee);
        cardTag = findViewById(R.id.cardTag);
        
        tvParsedStartDate = findViewById(R.id.tvParsedStartDate);
        tvParsedDueDate = findViewById(R.id.tvParsedDueDate);
        tvParsedPriority = findViewById(R.id.tvParsedPriority);
        tvParsedAssignee = findViewById(R.id.tvParsedAssignee);
        tvParsedTag = findViewById(R.id.tvParsedTag);
        
        ivParsedStartDate = findViewById(R.id.ivParsedStartDate);
        ivParsedDueDate = findViewById(R.id.ivParsedDueDate);
        ivParsedAssignee = findViewById(R.id.ivParsedAssignee);
        ivParsedTag = findViewById(R.id.ivParsedTag);

        // Animate entrance
        bgOverlay.setAlpha(0f);
        bgOverlay.animate().alpha(1f).setDuration(250).start();
        bottomSheet.setTranslationY(1500f);
        bottomSheet.animate().translationY(0f).setDuration(300).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        // Clicks mapping
        bgOverlay.setOnClickListener(v -> closeActivity());
        bottomSheet.setOnClickListener(v -> {
            etPrompt.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(bottomSheet.getWindowToken(), 0);
        });

        // Request keyboard immediately
        etPrompt.requestFocus();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etPrompt, InputMethodManager.SHOW_IMPLICIT);
        }, 100);

        // Dialog interactions
        cardStartDate.setOnClickListener(v -> showCustomDatePickerDialog(tvParsedStartDate, "Bắt đầu: ", cardStartDate, ivParsedStartDate, "#EFF6FF", "#1D4ED8"));
        cardDueDate.setOnClickListener(v -> showCustomDatePickerDialog(tvParsedDueDate, "Hạn: ", cardDueDate, ivParsedDueDate, "#EFF6FF", "#1D4ED8"));
        cardPriority.setOnClickListener(v -> showPriorityDialog());
        cardAssignee.setOnClickListener(v -> showAssigneeDialog());
        cardTag.setOnClickListener(v -> showTagDialog());

        // Save
        btnSaveTask.setOnClickListener(v -> {
            if (etPrompt.getText().toString().trim().isEmpty()) {
                 Toast.makeText(this, "Nội dung Task không được để trống!", Toast.LENGTH_SHORT).show();
                 return;
            }
            Toast.makeText(this, "Đã lưu Task: " + etPrompt.getText().toString(), Toast.LENGTH_LONG).show();
            closeActivity();
        });

        parseRunnable = this::simulateNLP;
        etPrompt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                parseHandler.removeCallbacks(parseRunnable);
                parseHandler.postDelayed(parseRunnable, 600); // 600ms debounce
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void simulateNLP() {
        String rawText = etPrompt.getText().toString();
        String text = rawText.toLowerCase();

        // Tự động phỏng đoán Tên Task nếu để trống Tiêu Đề
        if (!rawText.isEmpty() && etParsedTitle.getText().toString().isEmpty() && rawText.length() > 5) {
            String initialTitle = rawText.split("\n")[0]; // Lấy dòng đầu tiên làm Title
            etParsedTitle.setText(initialTitle.length() > 30 ? initialTitle.substring(0, 30) + "..." : initialTitle);
        }

        // Check Priority
        if (text.contains("gấp") || text.contains("high") || text.contains("khẩn")) {
            tvParsedPriority.setText("🚩");
            setCardStyle(cardPriority, tvParsedPriority, null, "#FEF2F2", "#B91C1C");
        } else if (text.contains("vừa") || text.contains("medium")) {
            tvParsedPriority.setText("🏁");
            setCardStyle(cardPriority, tvParsedPriority, null, "#FFF7ED", "#C2410C");
        } else if (text.contains("chậm") || text.contains("low")) {
            tvParsedPriority.setText("🏳️");
            setCardStyle(cardPriority, tvParsedPriority, null, "#F1F5F9", "#A1A1AA");
        }

        // Check Assignee
        if (text.contains("đức")) {
            tvParsedAssignee.setText("@Đức");
            setCardStyle(cardAssignee, tvParsedAssignee, ivParsedAssignee, "#F3E8FF", "#7E22CE");
        } else if (text.contains("linh")) {
            tvParsedAssignee.setText("@Linh");
            setCardStyle(cardAssignee, tvParsedAssignee, ivParsedAssignee, "#F3E8FF", "#7E22CE");
        }

        // Check Date Due vs Start
        if (text.contains("mai") || text.contains("tomorrow")) {
            tvParsedDueDate.setText("Ngày mai");
            setCardStyle(cardDueDate, tvParsedDueDate, ivParsedDueDate, "#EFF6FF", "#1D4ED8");
        }
        
        // Check Tag (Frontend / Backend)
        if (text.contains("code") || text.contains("dev")) {
            tvParsedTag.setText("#Backend");
            setCardStyle(cardTag, tvParsedTag, ivParsedTag, "#FCE7F3", "#BE185D");
        }
    }

    private void showPriorityDialog() {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, cardPriority);
        String[] priorities = {"Khẩn Cấp 🚩", "Cơ bản 🏁", "Rảnh rỗi 🏳️"};
        for (int i = 0; i < priorities.length; i++) {
            popup.getMenu().add(0, i, i, priorities[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            int which = item.getItemId();
            String flag;
            String fgHex, bgHex;
            String wordToBold;
            if (which == 0) { // HIGH
                flag = "🚩";
                bgHex = "#FEF2F2";
                fgHex = "#B91C1C";
                wordToBold = "Khẩn cấp";
            } else if (which == 1) { // MEDIUM
                flag = "🏁";
                bgHex = "#FFF7ED";
                fgHex = "#C2410C";
                wordToBold = "Cơ bản";
            } else { // LOW
                flag = "🏳️";
                bgHex = "#F1F5F9";
                fgHex = "#475569";
                wordToBold = "Rảnh";
            }
            tvParsedPriority.setText(flag);
            setCardStyle(cardPriority, tvParsedPriority, null, bgHex, fgHex);
            appendChipToPrompt(wordToBold, fgHex);
            return true;
        });
        popup.show();
    }

    private void showAssigneeDialog() {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, cardAssignee);
        String[] members = {"Đức", "Linh", "Hải", "Nghĩa", "Bỏ chọn"};
        for (int i = 0; i < members.length; i++) {
            popup.getMenu().add(0, i, i, members[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            int which = item.getItemId();
            if (which == 4) {
                tvParsedAssignee.setText("Phân công");
                cardAssignee.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.theme_surface_variant));
                tvParsedAssignee.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.theme_text_hint));
                ivParsedAssignee.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.theme_text_hint));
            } else {
                tvParsedAssignee.setText("@" + members[which]);
                setCardStyle(cardAssignee, tvParsedAssignee, ivParsedAssignee, "#F3E8FF", "#7E22CE");
                appendChipToPrompt("@" + members[which], "#7E22CE");
            }
            return true;
        });
        popup.show();
    }
    
    private void showTagDialog() {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, cardTag);
        String[] tags = {"Backend", "Frontend", "Design", "Bug", "Bỏ chọn"};
        for (int i = 0; i < tags.length; i++) {
            popup.getMenu().add(0, i, i, tags[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            int which = item.getItemId();
            if (which == 4) {
                tvParsedTag.setText("Nhãn");
                cardTag.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.theme_surface_variant));
                tvParsedTag.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.theme_text_hint));
                ivParsedTag.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.theme_text_hint));
            } else {
                tvParsedTag.setText("#" + tags[which]);
                setCardStyle(cardTag, tvParsedTag, ivParsedTag, "#FCE7F3", "#BE185D");
                appendChipToPrompt("#" + tags[which], "#BE185D");
            }
            return true;
        });
        popup.show();
    }

    private void showCustomDatePickerDialog(TextView targetTv, String prefix, CardView parentCard, ImageView targetIcon, String bgHex, String fgHex) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.dialog_datetime_picker, null);
        dialog.setContentView(view);

        android.widget.CalendarView calendarView = view.findViewById(R.id.calendarView);
        LinearLayout layoutTimePicker = view.findViewById(R.id.layoutTimePicker);
        TextView tvSelectedTime = view.findViewById(R.id.tvSelectedTime);
        android.widget.Button btnSaveDateTime = view.findViewById(R.id.btnSaveDateTime);

        final int[] selectedYear = {java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)};
        final int[] selectedMonth = {java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)};
        final int[] selectedDay = {java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)};
        final int[] selectedHour = {8};
        final int[] selectedMinute = {0};

        tvSelectedTime.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", selectedHour[0], selectedMinute[0]));

        calendarView.setOnDateChangeListener((v, year, month, dayOfMonth) -> {
            selectedYear[0] = year;
            selectedMonth[0] = month;
            selectedDay[0] = dayOfMonth;
        });

        layoutTimePicker.setOnClickListener(v -> {
            new android.app.TimePickerDialog(this, (timePicker, hourOfDay, minute) -> {
                selectedHour[0] = hourOfDay;
                selectedMinute[0] = minute;
                tvSelectedTime.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
            }, selectedHour[0], selectedMinute[0], true).show();
        });

        btnSaveDateTime.setOnClickListener(v -> {
            String shortDate = formatShortDate(selectedDay[0], selectedMonth[0] + 1, selectedYear[0], selectedHour[0], selectedMinute[0]);
            targetTv.setText(prefix + shortDate);
            setCardStyle(parentCard, targetTv, targetIcon, bgHex, fgHex);
            appendChipToPrompt(prefix + shortDate, fgHex);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void setCardStyle(CardView card, TextView tv, ImageView icon, String bgHex, String fgHex) {
        card.setCardBackgroundColor(Color.parseColor(bgHex)); // Bật Bảng nền đặc lại
        int fgColor = Color.parseColor(fgHex);
        tv.setTextColor(fgColor);
        if (icon != null) {
            icon.setColorFilter(fgColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    private String formatShortDate(int day, int month, int year, int hour, int minute) {
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        if (year == currentYear) {
            return String.format(java.util.Locale.getDefault(), "%02d/%02d %02d:%02d", day, month, hour, minute);
        }
        return String.format(java.util.Locale.getDefault(), "%02d/%02d/%04d %02d:%02d", day, month, year, hour, minute);
    }

    private void appendChipToPrompt(String text, String fgHex) {
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(etPrompt.getText());
        if (ssb.length() > 0 && ssb.charAt(ssb.length() - 1) != ' ' && ssb.charAt(ssb.length() - 1) != '\n') {
            ssb.append(" ");
        }
        int start = ssb.length();
        ssb.append(text);
        int end = ssb.length();
        
        int fgColor = Color.parseColor(fgHex);
        ssb.setSpan(new android.text.style.ForegroundColorSpan(fgColor), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        ssb.append(" ");
        etPrompt.setText(ssb);
        etPrompt.setSelection(etPrompt.getText().length());
    }

    private void closeActivity() {
        bgOverlay.animate().alpha(0f).setDuration(250).start();
        bottomSheet.animate().translationY(1500f).setDuration(250).withEndAction(() -> {
            finish();
            overridePendingTransition(0, 0);
        }).start();
    }

    @Override
    public void onBackPressed() {
        closeActivity();
    }
}
