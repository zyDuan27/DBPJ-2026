package com.campus.activity.service;

import com.campus.activity.config.LlmProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleLlmClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void chatJsonReadsOpenAiCompatibleResponseAsRawStringBeforeParsing() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] response = """
                    {
                      "id": "chatcmpl-test",
                      "object": "chat.completion",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "{\\"intent\\":\\"CREDIT_LOW_STUDENTS\\",\\"domain\\":\\"credit\\",\\"selectFields\\":[\\"student.name\\"]}"
                          },
                          "finish_reason": "stop"
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setModel("test-model");

        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, JsonMapper.builder().build());

        String content = client.chatJson("system", "user");

        assertThat(content).contains("\"intent\":\"CREDIT_LOW_STUDENTS\"");
    }
}
