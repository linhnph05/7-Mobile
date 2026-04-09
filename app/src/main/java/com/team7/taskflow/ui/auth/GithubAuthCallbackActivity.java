package com.team7.taskflow.ui.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.dashboard.DashboardActivity;

public class GithubAuthCallbackActivity extends BaseActivity {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		handleIntent(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		handleIntent(intent);
	}

	private void handleIntent(Intent intent) {
		Uri data = intent != null ? intent.getData() : null;
		GithubAuthHelper.handleOAuthRedirect(data, new GithubAuthHelper.GithubAuthCallback() {
			@Override
			public void onSuccess(String userId) {
				runOnUiThread(() -> {
					Intent dashboardIntent = new Intent(GithubAuthCallbackActivity.this, DashboardActivity.class);
					dashboardIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
					startActivity(dashboardIntent);
					finish();
				});
			}

			@Override
			public void onError(String message) {
				runOnUiThread(() -> {
					Toast.makeText(GithubAuthCallbackActivity.this, message, Toast.LENGTH_LONG).show();
					Intent loginIntent = new Intent(GithubAuthCallbackActivity.this, LoginActivity.class);
					loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
					startActivity(loginIntent);
					finish();
				});
			}
		});
	}
}
