package com.example.project_mobile;

/**
 * Supabase project configuration.
 *
 * Values are injected at build time from the .env file in the project root.
 * Edit .env (never commit it) to set your credentials:
 *
 *   SUPABASE_URL=https://your-project-ref.supabase.co
 *   SUPABASE_ANON_KEY=your-anon-key
 *
 * Both values are found in:
 *   Supabase Dashboard → Project Settings → API
 */
public final class SupabaseConfig {

    /** Project URL injected from .env → SUPABASE_URL */
    public static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;

    /** Public anon key injected from .env → SUPABASE_ANON_KEY */
    public static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;

    private SupabaseConfig() { /* no instances */ }
}
