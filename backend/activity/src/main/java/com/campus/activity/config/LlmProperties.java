package com.campus.activity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private boolean enabled;
    private String provider = "openai-compatible";
    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4.1-mini";
    private int timeoutMs = 15000;
    private boolean responseFormatEnabled = false;
    private boolean repairEnabled = false;
    private boolean summaryEnabled = false;
    private String sqlMode = "ADMIN_ONLY";
    private boolean adminSqlEnabled = true;
    private boolean controlledSqlEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isResponseFormatEnabled() {
        return responseFormatEnabled;
    }

    public void setResponseFormatEnabled(boolean responseFormatEnabled) {
        this.responseFormatEnabled = responseFormatEnabled;
    }

    public boolean isRepairEnabled() {
        return repairEnabled;
    }

    public void setRepairEnabled(boolean repairEnabled) {
        this.repairEnabled = repairEnabled;
    }

    public boolean isSummaryEnabled() {
        return summaryEnabled;
    }

    public void setSummaryEnabled(boolean summaryEnabled) {
        this.summaryEnabled = summaryEnabled;
    }

    public String getSqlMode() {
        return sqlMode;
    }

    public void setSqlMode(String sqlMode) {
        this.sqlMode = sqlMode;
    }

    public boolean isAdminSqlEnabled() {
        return adminSqlEnabled;
    }

    public void setAdminSqlEnabled(boolean adminSqlEnabled) {
        this.adminSqlEnabled = adminSqlEnabled;
    }

    public boolean isControlledSqlEnabled() {
        return controlledSqlEnabled;
    }

    public void setControlledSqlEnabled(boolean controlledSqlEnabled) {
        this.controlledSqlEnabled = controlledSqlEnabled;
    }

    public boolean isReady() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
