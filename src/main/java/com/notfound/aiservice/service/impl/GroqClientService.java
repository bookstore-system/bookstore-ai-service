package com.notfound.aiservice.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.model.dto.request.AttachmentRequest;
import com.notfound.aiservice.model.dto.response.AgentChatResponse;
import com.notfound.aiservice.service.AiModelClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${ai.provider:}' == 'groq' || '${ai.provider:}' == 'openai-compatible'")
public class GroqClientService implements AiModelClient {

    private static final int MAX_TOOL_ITERATIONS = 4;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Value("${ai.provider:groq}")
    private String provider;

    @Value("${openai-compatible.api.key:${groq.api.key:}}")
    private String apiKey;

    @Value("${openai-compatible.model:${groq.model:llama-3.3-70b-versatile}}")
    private String model;

    @Value("${openai-compatible.base-url:${groq.base-url:https://api.groq.com/openai}}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        log.info("AI provider active: {}, model={}, baseUrl={}, configured={}",
                provider, model, normalizedBaseUrl(), isConfigured());
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String ask(String prompt) {
        if (!isConfigured()) {
            return "AI service chua cau hinh API key.";
        }
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", prompt == null ? "" : prompt));
            Map<String, Object> response = chatCompletion(messages, List.of());
            return extractContent(response);
        } catch (Exception e) {
            log.warn("OpenAI-compatible AI ask failed: {}", e.getMessage());
            return "AI service khong the ket noi model: " + e.getMessage();
        }
    }

    @Override
    public String askMultimodal(String prompt, List<AttachmentRequest> attachments) {
        return ask(prompt);
    }

    @Override
    public String chatWithTools(
            String systemPrompt,
            List<String> historyMessages,
            String userMessage,
            Collection<Tool> tools,
            ToolContext context,
            List<AgentChatResponse.ToolCallTrace> trace
    ) {
        if (!isConfigured()) {
            return "AI service chua cau hinh API key.";
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt));
        String prompt = buildUserPrompt(historyMessages, userMessage);
        messages.add(Map.of("role", "user", "content", prompt));

        List<Map<String, Object>> toolSpecs = tools == null
                ? List.of()
                : tools.stream().map(this::toOpenAiToolSpec).toList();
        Map<String, Tool> toolByName = new LinkedHashMap<>();
        if (tools != null) {
            for (Tool tool : tools) {
                toolByName.put(tool.getName(), tool);
            }
        }

        try {
            for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
                Map<String, Object> response = chatCompletion(messages, toolSpecs);
                Map<String, Object> message = extractMessage(response);
                List<Map<String, Object>> toolCalls = extractToolCalls(message);

                if (toolCalls.isEmpty()) {
                    return asString(message.get("content"));
                }

                messages.add(message);
                for (Map<String, Object> toolCall : toolCalls) {
                    String toolCallId = asString(toolCall.get("id"));
                    Map<String, Object> function = asMap(toolCall.get("function"));
                    String toolName = asString(function.get("name"));
                    String argumentsJson = asString(function.get("arguments"));
                    Map<String, Object> args = parseToolArguments(argumentsJson);

                    ToolResult result = executeTool(toolByName.get(toolName), toolName, args, context);
                    trace.add(AgentChatResponse.ToolCallTrace.builder()
                            .toolName(toolName)
                            .arguments(args)
                            .success(result.isSuccess())
                            .errorMessage(result.getErrorMessage())
                            .data(result.getData())
                            .build());

                    messages.add(Map.of(
                            "role", "tool",
                            "tool_call_id", toolCallId == null ? "" : toolCallId,
                            "content", writeJson(toolResponse(result))
                    ));
                }
            }
            return "Minh da thu qua nhieu buoc goi tool nhung chua co cau tra loi cuoi.";
        } catch (Exception e) {
            log.warn("OpenAI-compatible AI chatWithTools failed: {}", e.getMessage());
            return "AI service loi: " + e.getMessage();
        }
    }

    private Map<String, Object> chatCompletion(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools
    ) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.7);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI API error " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    private String chatCompletionsUrl() {
        String value = normalizedBaseUrl();
        if (value.endsWith("/v1/chat/completions")) {
            return value;
        }
        return value + "/v1/chat/completions";
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null || baseUrl.isBlank() ? "https://api.groq.com/openai" : baseUrl;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String buildUserPrompt(List<String> historyMessages, String userMessage) {
        StringBuilder prompt = new StringBuilder();
        if (historyMessages != null && !historyMessages.isEmpty()) {
            prompt.append("Ngu canh hoi thoai gan day:\n");
            for (String h : historyMessages) {
                prompt.append("- ").append(h).append('\n');
            }
            prompt.append("\nTin nhan hien tai:\n");
        }
        prompt.append(userMessage == null ? "" : userMessage);
        return prompt.toString();
    }

    private Map<String, Object> toOpenAiToolSpec(Tool tool) {
        ToolSchema schema = tool.getSchema();
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", tool.getName(),
                        "description", schema.getDescription() == null ? "" : schema.getDescription(),
                        "parameters", toInputSchemaMap(schema)
                )
        );
    }

    private Map<String, Object> toInputSchemaMap(ToolSchema schema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (schema.getParameters() != null) {
            schema.getParameters().forEach((k, v) -> properties.put(k, toJsonSchema(v)));
        }
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            inputSchema.put("required", schema.getRequired());
        }
        return inputSchema;
    }

    private Map<String, Object> toJsonSchema(ToolSchema.ParameterSchema p) {
        Map<String, Object> node = new LinkedHashMap<>();
        if (p.getType() != null) node.put("type", p.getType());
        if (p.getDescription() != null) node.put("description", p.getDescription());
        if (p.getEnumValues() != null) node.put("enum", p.getEnumValues());
        if ("array".equalsIgnoreCase(p.getType()) && p.getItems() != null) {
            node.put("items", toJsonSchema(p.getItems()));
        }
        return node;
    }

    private ToolResult executeTool(Tool tool, String toolName, Map<String, Object> args, ToolContext context) {
        if (tool == null) {
            return ToolResult.fail(toolName, "Tool khong ton tai");
        }
        try {
            return tool.execute(args, context);
        } catch (Exception e) {
            log.warn("Tool {} failed", tool.getName(), e);
            return ToolResult.fail(tool.getName(), e.getMessage());
        }
    }

    private Map<String, Object> toolResponse(ToolResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.isSuccess());
        if (result.isSuccess()) {
            response.put("data", result.getData() == null ? Map.of() : result.getData());
        } else {
            response.put("error", result.getErrorMessage());
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMessage(Map<String, Object> response) {
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
            }
        }
        return Map.of("role", "assistant", "content", "");
    }

    private String extractContent(Map<String, Object> response) {
        return asString(extractMessage(response).get("content"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<String, Object> message) {
        Object value = message.get("tool_calls");
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private Map<String, Object> parseToolArguments(String input) {
        if (input == null || input.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(input, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Cannot parse tool arguments: {}", e.getMessage());
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
