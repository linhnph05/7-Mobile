package com.team7.taskflow.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for formatting date/time strings without timezone parsing.
 * Uses substring method to extract date (yyyy-MM-dd) and time (HH:mm) parts,
 * then formats to Vietnamese locale (dd/MM/yyyy HH:mm).
 *
 * Purpose: Handle database strings like "2026-03-25 00:00:00+07" safely
 * by extracting needed parts without attempting OffsetDateTime parsing.
 */
public class DateTimeFormatterUtil {

    /**
     * Format date string for display in Vietnam locale.
     * - Date only: dd/MM/yyyy
     * - Date-time: dd/MM/yyyy HH:mm
     * Ignores timezone info (+07) using substring method.
     */
    public static String formatDateDisplay(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return dateStr;
        }

        String normalized = dateStr.trim().replace('T', ' ');
        String datePart = extractDatePart(normalized);
        if (datePart == null) {
            return normalized;
        }

        String formattedDate = formatDatePart(datePart);
        String timePart = extractTimePart(normalized);
        return timePart == null ? formattedDate : formattedDate + " " + timePart;
    }

    /**
     * Extract date part (yyyy-MM-dd) from the beginning of string.
     */
    private static String extractDatePart(String value) {
        try {
            if (value != null && value.length() >= 10) {
                String candidate = value.substring(0, 10);
                LocalDate.parse(candidate, DateTimeFormatter.ISO_LOCAL_DATE);
                return candidate;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Convert date from yyyy-MM-dd to dd/MM/yyyy format.
     */
    private static String formatDatePart(String datePart) {
        try {
            LocalDate localDate = LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE);
            return localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {
        }
        return datePart;
    }

    /**
     * Extract time part (HH:mm) from position 11 onwards.
     * Handles full ISO datetime with timezone (2026-03-25 00:00:00+07).
     */
    private static String extractTimePart(String value) {
        if (value == null || value.length() <= 10) {
            return null;
        }
        try {
            String rawTime = value.substring(11).trim();
            if (rawTime.isEmpty()) {
                return null;
            }
            return rawTime.length() >= 5 ? rawTime.substring(0, 5) : rawTime;
        } catch (Exception ignored) {
        }
        return null;
    }
}
