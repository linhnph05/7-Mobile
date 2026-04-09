package com.team7.taskflow.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import com.team7.taskflow.data.remote.SupabaseConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GithubAuthHelper {

	private static final String DEFAULT_REDIRECT_URI = "taskai://auth-callback";
	private static GithubAuthCallback pendingCallback;

	public interface GithubAuthCallback {
		void onSuccess(String userId);

		void onError(String message);
	}

	public static void startOAuth(Context context, GithubAuthCallback callback) {
		pendingCallback = callback;

		String redirectUri = getRedirectUri();
		Uri authUri = Uri.parse(SupabaseConfig.SUPABASE_URL + "/auth/v1/authorize")
				.buildUpon()
				.appendQueryParameter("provider", "github")
				.appendQueryParameter("redirect_to", redirectUri)
				.build();

		Intent intent = new Intent(Intent.ACTION_VIEW, authUri);
		context.startActivity(intent);
	}

	public static boolean handleOAuthRedirect(Uri data) {
		return handleOAuthRedirect(data, null);
	}

	public static boolean handleOAuthRedirect(Uri data, GithubAuthCallback directCallback) {
		if (data == null) {
			notifyError("GitHub sign-in failed: missing callback data.", directCallback);
			return true;
		}

		String expectedPrefix = getRedirectUri();
		if (!data.toString().startsWith(expectedPrefix)) {
			return false;
		}

		Map<String, String> queryMap = parseUrlEncodedPairs(data.getEncodedQuery());
		Map<String, String> fragmentMap = parseUrlEncodedPairs(data.getEncodedFragment());

		String error = firstNonEmpty(
				fragmentMap.get("error_description"),
				fragmentMap.get("error"),
				queryMap.get("error_description"),
				queryMap.get("error"));
		if (!TextUtils.isEmpty(error)) {
			notifyError(error, directCallback);
			return true;
		}

		String accessToken = firstNonEmpty(fragmentMap.get("access_token"), queryMap.get("access_token"));
		String refreshToken = firstNonEmpty(fragmentMap.get("refresh_token"), queryMap.get("refresh_token"));

		if (TextUtils.isEmpty(accessToken)) {
			notifyError("GitHub sign-in failed: no access token returned.", directCallback);
			return true;
		}

		AuthRepository.signInWithGithubTokens(accessToken, refreshToken, new AuthRepository.AuthCallback() {
			@Override
			public void onSuccess(String userId) {
				notifySuccess(userId, directCallback);
			}

			@Override
			public void onError(String message) {
				notifyError(message, directCallback);
			}
		});
		return true;
	}

	private static void notifySuccess(String userId, GithubAuthCallback directCallback) {
		GithubAuthCallback callback = directCallback != null ? directCallback : pendingCallback;
		if (directCallback == null) {
			pendingCallback = null;
		}
		if (callback != null) {
			callback.onSuccess(userId);
		}
	}

	private static void notifyError(String message, GithubAuthCallback directCallback) {
		GithubAuthCallback callback = directCallback != null ? directCallback : pendingCallback;
		if (directCallback == null) {
			pendingCallback = null;
		}
		if (callback != null) {
			callback.onError(message);
		}
	}

	private static String getRedirectUri() {
		String configured = SupabaseConfig.GITHUB_REDIRECT_URI;
		return configured != null && !configured.trim().isEmpty() ? configured.trim() : DEFAULT_REDIRECT_URI;
	}

	private static String firstNonEmpty(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (!TextUtils.isEmpty(value)) {
				return value;
			}
		}
		return null;
	}

	private static Map<String, String> parseUrlEncodedPairs(String encoded) {
		Map<String, String> result = new LinkedHashMap<>();
		if (TextUtils.isEmpty(encoded)) {
			return result;
		}

		String[] parts = encoded.split("&");
		for (String part : parts) {
			if (TextUtils.isEmpty(part)) {
				continue;
			}
			String key;
			String value;
			int index = part.indexOf('=');
			if (index >= 0) {
				key = Uri.decode(part.substring(0, index));
				value = Uri.decode(part.substring(index + 1));
			} else {
				key = Uri.decode(part);
				value = "";
			}
			result.put(key, value);
		}
		return result;
	}

	private GithubAuthHelper() {
	}
}
