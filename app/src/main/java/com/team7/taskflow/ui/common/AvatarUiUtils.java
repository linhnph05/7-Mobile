package com.team7.taskflow.ui.common;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.team7.taskflow.R;

import java.util.Locale;

public final class AvatarUiUtils {

    private static final int DEFAULT_AVATAR_SIZE_DP = 40;
    private static final float LETTER_SIZE_RATIO = 0.42f;
    private static final float SMALL_AVATAR_LETTER_SIZE_RATIO = 0.52f;
    private static final int SMALL_AVATAR_THRESHOLD_DP = 28;

    private AvatarUiUtils() {
    }

    public static String resolveInitial(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "?";
        }
        return displayName.trim().substring(0, 1).toUpperCase(Locale.US);
    }

    public static void bindAvatarOrFallback(
            @NonNull ImageView avatarView,
            TextView letterView,
            String avatarUrl,
            String displayName) {
        String letter = resolveInitial(displayName);
        avatarView.setBackgroundResource(R.drawable.bg_avatar_grey_bordered);

        if (letterView != null) {
            letterView.setText(letter);
            styleLetterView(letterView, avatarView);
            letterView.setVisibility(View.VISIBLE);
        }

        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            showFallbackInsideAvatar(avatarView, letterView, letter);
            return;
        }

        // Check context validity before calling Glide
        android.content.Context context = avatarView.getContext();
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                showFallbackInsideAvatar(avatarView, letterView, letter);
                return;
            }
        }

        com.bumptech.glide.Glide.with(avatarView)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.bg_avatar_grey_bordered)
                .error(R.drawable.bg_avatar_grey_bordered)
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e,
                            Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            boolean isFirstResource) {
                        if (letterView != null) {
                            letterView.setVisibility(View.VISIBLE);
                        } else {
                            showFallbackInsideAvatar(avatarView, null, letter);
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                            Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            com.bumptech.glide.load.DataSource dataSource,
                            boolean isFirstResource) {
                        if (letterView != null) {
                            letterView.setVisibility(View.GONE);
                        }
                        return false;
                    }
                })
                .into(avatarView);
    }

    private static void styleLetterView(@NonNull TextView letterView, @NonNull ImageView avatarView) {
        int avatarSize = resolveAvatarSizePx(avatarView);
        float preferredTextSizePx = Math.max(spToPx(12f, letterView), avatarSize * resolveLetterSizeRatio(avatarView, avatarSize));
        letterView.setTextSize(TypedValue.COMPLEX_UNIT_PX, preferredTextSizePx);
        letterView.setTextColor(ContextCompat.getColor(letterView.getContext(), R.color.white));
        letterView.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private static void showFallbackInsideAvatar(
            @NonNull ImageView avatarView,
            TextView letterView,
            @NonNull String letter) {
        if (letterView != null) {
            avatarView.setImageDrawable(null);
            return;
        }
        avatarView.setImageBitmap(createLetterBitmap(avatarView, letter));
    }

    private static Bitmap createLetterBitmap(@NonNull ImageView avatarView, @NonNull String letter) {
        int sizePx = resolveAvatarSizePx(avatarView);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ContextCompat.getColor(avatarView.getContext(), R.color.white));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(Math.max(spToPx(12f, avatarView), sizePx * resolveLetterSizeRatio(avatarView, sizePx)));

        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float centerX = sizePx / 2f;
        float centerY = (sizePx / 2f) - ((fontMetrics.ascent + fontMetrics.descent) / 2f);
        canvas.drawText(letter, centerX, centerY, paint);
        return bitmap;
    }

    private static int resolveAvatarSizePx(@NonNull ImageView avatarView) {
        int width = avatarView.getWidth();
        int height = avatarView.getHeight();
        int size = Math.max(width, height);
        if (size > 0) {
            return size;
        }

        if (avatarView.getLayoutParams() != null) {
            size = Math.max(avatarView.getLayoutParams().width, avatarView.getLayoutParams().height);
            if (size > 0) {
                return size;
            }
        }

        float density = avatarView.getResources().getDisplayMetrics().density;
        return Math.round(DEFAULT_AVATAR_SIZE_DP * density);
    }

    private static float resolveLetterSizeRatio(@NonNull View view, int avatarSizePx) {
        float density = view.getResources().getDisplayMetrics().density;
        int thresholdPx = Math.round(SMALL_AVATAR_THRESHOLD_DP * density);
        if (avatarSizePx <= thresholdPx) {
            return SMALL_AVATAR_LETTER_SIZE_RATIO;
        }
        return LETTER_SIZE_RATIO;
    }

    private static float spToPx(float sp, @NonNull View view) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                view.getResources().getDisplayMetrics());
    }
}
