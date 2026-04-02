package com.team7.taskflow.ui.attachment;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.team7.taskflow.R;
import com.team7.taskflow.ui.base.BaseActivity;

/**
 * Hiển thị ảnh fullscreen khi user nhấn preview.
 * Nhận vào:
 *   - "image_url"  (String) — URL ảnh đã upload lên Supabase Storage
 *   - "image_uri"  (String) — URI local (ảnh chưa upload)
 *   - "title"      (String) — Tên file hiển thị trên toolbar
 */
public class FullscreenImageActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        ImageView ivFullscreen = findViewById(R.id.ivFullscreen);
        TextView  tvTitle      = findViewById(R.id.tvTitle);
        View      btnClose     = findViewById(R.id.btnClose);

        String title    = getIntent().getStringExtra("title");
        String imageUrl = getIntent().getStringExtra("image_url");
        String imageUri = getIntent().getStringExtra("image_uri");

        if (tvTitle != null && title != null) tvTitle.setText(title);
        if (btnClose != null) btnClose.setOnClickListener(v -> finish());

        if (ivFullscreen != null) {
            if (imageUrl != null) {
                // Load từ URL (Supabase Storage)
                Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_attach_file)
                        .error(R.drawable.ic_attach_file)
                        .into(ivFullscreen);
            } else if (imageUri != null) {
                // Load từ URI local
                Glide.with(this)
                        .load(Uri.parse(imageUri))
                        .placeholder(R.drawable.ic_attach_file)
                        .error(R.drawable.ic_attach_file)
                        .into(ivFullscreen);
            }
        }
    }
}