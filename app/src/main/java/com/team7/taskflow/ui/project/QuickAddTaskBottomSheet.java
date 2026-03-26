package com.team7.taskflow.ui.project;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.team7.taskflow.R;

public class QuickAddTaskBottomSheet extends BottomSheetDialogFragment {

    public interface QuickAddCallback {
        void onDataEntered(String title, String status);
    }

    private QuickAddCallback callback;
    private TextInputEditText etTitle;
    private ChipGroup chipGroup;

    public void setCallback(QuickAddCallback callback) {
        this.callback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_quick_add_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etTitle = view.findViewById(R.id.etTaskTitle);
        chipGroup = view.findViewById(R.id.chipGroupStatus);
        Button btnSave = view.findViewById(R.id.btnSaveTask);

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                etTitle.setError("Title cannot be empty");
                return;
            }

            int checkedId = chipGroup.getCheckedChipId();
            String status = "TODO";
            if (checkedId == R.id.chipDoing) status = "DOING";
            else if (checkedId == R.id.chipDone) status = "DONE";

            if (callback != null) {
                callback.onDataEntered(title, status);
            }
            dismiss();
        });
    }
}
