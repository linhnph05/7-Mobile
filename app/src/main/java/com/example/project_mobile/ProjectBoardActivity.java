package com.example.project_mobile;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class ProjectBoardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_project_board);

        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // FAB
        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            // TODO: open add task sheet
        });

        // Tab switching visual feedback
        setupTabs();
    }

    private void setupTabs() {
        LinearLayout tabToDo  = findViewById(R.id.tabToDo);
        LinearLayout tabDoing = findViewById(R.id.tabDoing);
        LinearLayout tabDone  = findViewById(R.id.tabDone);

        View[] tabs = {tabToDo, tabDoing, tabDone};
        for (View tab : tabs) {
            tab.setOnClickListener(v -> {
                // visual-only; no data switch needed for static screen
            });
        }
    }
}
