package com.team7.taskflow.data.remote;

import com.team7.taskflow.BuildConfig;

/**
 * Supabase configuration constants.
 */
public class SupabaseConfig {

    public static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;

    public static final String REST_URL = SUPABASE_URL + "/rest/v1/";

    public static final String AUTH_URL = SUPABASE_URL + "/auth/v1/";

    public static final String STORAGE_URL = SUPABASE_URL + "/storage/v1/";

    public static final String SUPABASE_KEY = BuildConfig.SUPABASE_ANON_KEY;

    // Headers
    public static final String HEADER_API_KEY = "apikey";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_PREFER = "Prefer";

    // Content types
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String PREFER_RETURN_REPRESENTATION = "return=representation";

    public static final String GOOGLE_WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID;
}
