package com.team7.taskflow.data.remote;

import com.team7.taskflow.data.remote.AiService.ParsedTask;

/**
 * Callback for AI parsing results.
 */
public interface AiCallback {
    void onSuccess(ParsedTask result);
    void onError(String error);
}
