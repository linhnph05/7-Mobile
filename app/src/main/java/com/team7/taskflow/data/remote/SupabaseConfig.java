package com.team7.taskflow.data.remote;

import com.team7.taskflow.BuildConfig;

/**
 * Supabase configuration constants.
 *
 * URL and Key are injected at build time from .env file (never commit .env).
 * Edit .env in project root to set your credentials:
 *
 * SUPABASE_URL=https://your-project-ref.supabase.co
 * SUPABASE_ANON_KEY=your-anon-key
 */
public class SupabaseConfig {

    // Supabase Project URL (from .env → BuildConfig)
    public static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;

    // Supabase REST API base URL
    public static final String REST_URL = SUPABASE_URL + "/rest/v1/";

    // Supabase Auth URL
    public static final String AUTH_URL = SUPABASE_URL + "/auth/v1/";

    // Supabase Anon/Public Key (from .env → BuildConfig)
    public static final String SUPABASE_KEY = BuildConfig.SUPABASE_ANON_KEY;

    // Headers
    public static final String HEADER_API_KEY = "apikey";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_PREFER = "Prefer";

    // Content types
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String PREFER_RETURN_REPRESENTATION = "return=representation";

    // Google OAuth
    public static final String GOOGLE_WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID;
}
