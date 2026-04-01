package com.team7.taskflow.ui.project;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.team7.taskflow.R;

import java.util.List;
import java.util.Locale;

public class HistoryEventAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<String> rows;

    public HistoryEventAdapter(Context context, List<String> rows) {
        this.inflater = LayoutInflater.from(context);
        this.rows = rows;
    }

    @Override
    public int getCount() {
        return rows != null ? rows.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_history_event, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String raw = rows.get(position);
        bindRow(holder, raw != null ? raw : "");
        return convertView;
    }

    private void bindRow(ViewHolder holder, String raw) {
        // Expected format: "dd/MM HH:mm - ACTION (OLD -> NEW)"
        String time = "Vừa xong";
        String action = raw;
        String detail = "";

        String[] splitDash = raw.split(" - ", 2);
        if (splitDash.length == 2) {
            time = splitDash[0].trim();
            String right = splitDash[1].trim();

            int open = right.indexOf('(');
            int close = right.lastIndexOf(')');
            if (open > 0 && close > open) {
                action = right.substring(0, open).trim();
                detail = right.substring(open + 1, close).trim();
            } else {
                action = right;
            }
        }

        int accentColor = resolveAccentColor(action, detail);
        holder.viewAccent.setBackgroundColor(accentColor);
        holder.tvTime.setText(time);
        holder.tvAction.setText(prettyActionLabel(action));
        holder.tvAction.setTextColor(accentColor);

        if (detail.isEmpty()) {
            holder.tvDetail.setVisibility(View.GONE);
        } else {
            holder.tvDetail.setVisibility(View.VISIBLE);
            holder.tvDetail.setText(detail);
        }
    }

    private String prettyActionLabel(String actionRaw) {
        if (actionRaw == null || actionRaw.trim().isEmpty()) {
            return "Cap nhat";
        }

        String normalized = actionRaw.trim().toUpperCase(Locale.US);
        if (normalized.contains("STATUS_CHANGED")) {
            return "Doi trang thai";
        }
        if (normalized.contains("COMMENT")) {
            return "Binh luan";
        }
        if (normalized.contains("DELETE") || normalized.contains("TRASH")) {
            return "Xoa / Thung rac";
        }
        if (normalized.contains("CREATE")) {
            return "Tao moi";
        }
        if (normalized.contains("UPDATE") || normalized.contains("EDIT")) {
            return "Chinh sua";
        }
        return normalized.replace('_', ' ');
    }

    private int resolveAccentColor(String actionRaw, String detailRaw) {
        String action = actionRaw != null ? actionRaw.toUpperCase(Locale.US) : "";
        String detail = detailRaw != null ? detailRaw.toUpperCase(Locale.US) : "";

        if (action.contains("STATUS_CHANGED")) {
            if (detail.contains("-> TRASH")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.text_red_600);
            }
            if (detail.contains("TRASH ->")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.success);
            }
            if (detail.contains("-> DONE")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.success);
            }
            if (detail.contains("-> IN_PROGRESS") || detail.contains("-> DOING")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.indigo_600);
            }
            return ContextCompat.getColor(inflater.getContext(), R.color.warning);
        }

        if (action.contains("COMMENT")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.indigo_600);
        }

        if (action.contains("DELETE") || action.contains("TRASH")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.text_red_600);
        }

        if (action.contains("CREATE")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.success);
        }

        if (action.contains("UPDATE") || action.contains("EDIT")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.slate_700);
        }

        return Color.parseColor("#475569");
    }

    private static class ViewHolder {
        final View viewAccent;
        final TextView tvTime;
        final TextView tvAction;
        final TextView tvDetail;

        ViewHolder(View itemView) {
            viewAccent = itemView.findViewById(R.id.viewHistoryAccent);
            tvTime = itemView.findViewById(R.id.tvHistoryTime);
            tvAction = itemView.findViewById(R.id.tvHistoryAction);
            tvDetail = itemView.findViewById(R.id.tvHistoryDetail);
        }
    }
}
