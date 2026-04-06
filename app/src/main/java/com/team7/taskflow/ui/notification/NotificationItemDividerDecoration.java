package com.team7.taskflow.ui.notification;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Draws a thin divider between RecyclerView rows.
 */
public class NotificationItemDividerDecoration extends RecyclerView.ItemDecoration {

    private final Paint paint;
    private final int heightPx;

    public NotificationItemDividerDecoration(@ColorInt int color, int heightPx) {
        this.paint = new Paint();
        this.paint.setColor(color);
        this.paint.setStyle(Paint.Style.FILL);
        this.heightPx = Math.max(1, heightPx);
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int childCount = parent.getChildCount();
        if (childCount <= 1) {
            return;
        }

        int left = parent.getPaddingLeft();
        int right = parent.getWidth() - parent.getPaddingRight();

        for (int i = 0; i < childCount - 1; i++) {
            View child = parent.getChildAt(i);
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
            int top = child.getBottom() + params.bottomMargin;
            int bottom = top + heightPx;
            canvas.drawRect(left, top, right, bottom, paint);
        }
    }
}