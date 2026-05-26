package com.campus.activity.service;

public interface LlmClient {
    String chatJson(String systemPrompt, String userPrompt);
}
