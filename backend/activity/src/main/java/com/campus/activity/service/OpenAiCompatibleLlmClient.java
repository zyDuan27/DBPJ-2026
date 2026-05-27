package com.campus.activity.service;

import com.campus.activity.common.BusinessException;
import com.campus.activity.config.LlmProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {
    private final LlmProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt) {
        if (!properties.isReady()) {
            throw new BusinessException(40002, "LLM 未启用或缺少 API Key");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("temperature", 0.1);
        if (properties.isResponseFormatEnabled()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new BusinessException(40002, "LLM 调用失败：HTTP " + ex.getStatusCode().value()
                    + "，响应：" + abbreviate(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            throw new BusinessException(40002, "LLM 调用失败：" + ex.getMessage());
        }
        Map<?, ?> response = parseResponse(responseBody);
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new BusinessException(40002, "LLM 未返回可用查询计划");
        }
        Object first = choices.getFirst();
        if (!(first instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)
                || !(message.get("content") instanceof String content)
                || content.isBlank()) {
            throw new BusinessException(40002, "LLM 查询计划格式异常");
        }
        return content;
    }

    private Map<?, ?> parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new BusinessException(40002, "LLM 返回空响应");
        }
        try {
            return objectMapper.readValue(responseBody, Map.class);
        } catch (Exception ex) {
            throw new BusinessException(40002, "LLM 响应不是合法 JSON：" + abbreviate(responseBody));
        }
    }

    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500) + "...";
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
