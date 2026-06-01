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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiClientService implements AiModelClient {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.google.genai.api-key:${gemini.api.key:}}")
    private String apiKey;

    @Value("${spring.ai.google.genai.chat.options.model:${gemini.model:gemini-2.5-flash}}")
    private String model;

    @PostConstruct
    void init() {
        log.info("AI provider active: gemini, model={}, configured={}", model, isConfigured());
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
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return "AI service chua cau hinh Spring AI ChatModel.";
        }
        try {
            return ChatClient.create(chatModel)
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Spring AI ask failed: {}", e.getMessage());
            return "AI service khong the ket noi Gemini: " + e.getMessage();
        }
    }

    @Override
    public String askMultimodal(String prompt, List<AttachmentRequest> attachments) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return "AI service chua cau hinh Spring AI ChatModel.";
        }

        List<Media> media = buildImageMedia(attachments);
        log.info(
                "Spring AI multimodal request: attachments={}, media={}",
                attachments == null ? 0 : attachments.size(),
                media.size()
        );
        try {
            return ChatClient.create(chatModel)
                    .prompt()
                    .user(u -> {
                        u.text(prompt);
                        if (!media.isEmpty()) {
                            u.media(media.toArray(Media[]::new));
                        }
                    })
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Spring AI multimodal failed: {}", e.getMessage());
            return "AI service khong xu ly duoc anh: " + e.getMessage();
        }
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
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return "AI service chua cau hinh Spring AI ChatModel.";
        }

        String prompt = buildUserPrompt(historyMessages, userMessage);
        List<ToolCallback> callbacks = tools == null
                ? List.of()
                : tools.stream()
                .map(tool -> new AgentToolCallback(tool, context, trace))
                .map(ToolCallback.class::cast)
                .toList();

        try {
            return ChatClient.create(chatModel)
                    .prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .toolCallbacks(callbacks)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn(
                    "Spring AI chatWithTools failed: model={}, tools={}, rootCause={}",
                    model,
                    tools == null ? List.of() : tools.stream().map(Tool::getName).toList(),
                    rootCauseMessage(e),
                    e
            );
            return chatWithoutToolCallbacks(chatModel, systemPrompt, prompt, tools, e);
        }
    }

    private String chatWithoutToolCallbacks(
            ChatModel chatModel,
            String systemPrompt,
            String prompt,
            Collection<Tool> tools,
            Exception toolCallingError
    ) {
        try {
            return ChatClient.create(chatModel)
                    .prompt()
                    .system(systemPrompt)
                    .user(buildNoToolFallbackPrompt(prompt, tools))
                    .call()
                    .content();
        } catch (Exception fallbackError) {
            log.warn(
                    "Spring AI fallback chat failed after tool-calling error. toolError={}, fallbackRootCause={}",
                    rootCauseMessage(toolCallingError),
                    rootCauseMessage(fallbackError),
                    fallbackError
            );
            return "AI service loi: " + rootCauseMessage(fallbackError);
        }
    }

    private String buildNoToolFallbackPrompt(String prompt, Collection<Tool> tools) {
        StringBuilder fallbackPrompt = new StringBuilder();
        fallbackPrompt.append(prompt == null ? "" : prompt);
        if (tools != null && !tools.isEmpty()) {
            fallbackPrompt.append("\n\n[Luu y he thong: Yeu cau tool-calling vua bi Gemini tu choi. ");
            fallbackPrompt.append("Hay tra loi ngan gon bang kien thuc hien co. ");
            fallbackPrompt.append("Neu can du lieu thuc te tu nha sach, hay noi ro ban can tra cuu them thay vi bia thong tin. ");
            fallbackPrompt.append("Cac tool kha dung: ");
            fallbackPrompt.append(tools.stream().map(Tool::getName).toList());
            fallbackPrompt.append("]");
        }
        return fallbackPrompt.toString();
    }

    private String rootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }

        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null || root.getMessage().isBlank()
                ? root.getClass().getSimpleName()
                : root.getMessage();
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

    private String toInputSchemaJson(ToolSchema schema) {
        try {
            return objectMapper.writeValueAsString(toInputSchemaMap(schema));
        } catch (Exception e) {
            log.warn("Cannot serialize tool schema {}", schema.getName(), e);
            return "{\"type\":\"object\",\"properties\":{}}";
        }
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

    private class AgentToolCallback implements ToolCallback {
        private final Tool tool;
        private final ToolContext context;
        private final List<AgentChatResponse.ToolCallTrace> trace;
        private final ToolDefinition definition;

        AgentToolCallback(Tool tool, ToolContext context, List<AgentChatResponse.ToolCallTrace> trace) {
            this.tool = tool;
            this.context = context;
            this.trace = trace;
            ToolSchema schema = tool.getSchema();
            this.definition = DefaultToolDefinition.builder()
                    .name(tool.getName())
                    .description(schema.getDescription())
                    .inputSchema(toInputSchemaJson(schema))
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String input) {
            Map<String, Object> args = parseToolArguments(input);
            ToolResult result;
            try {
                result = tool.execute(args, context);
            } catch (Exception e) {
                log.warn("Tool {} failed", tool.getName(), e);
                result = ToolResult.fail(tool.getName(), e.getMessage());
            }

            synchronized (trace) {
                trace.add(AgentChatResponse.ToolCallTrace.builder()
                        .toolName(tool.getName())
                        .arguments(args)
                        .success(result.isSuccess())
                        .errorMessage(result.getErrorMessage())
                        .data(result.getData())
                        .build());
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", result.isSuccess());
            if (result.isSuccess()) {
                response.put("data", result.getData() == null ? Map.of() : result.getData());
            } else {
                response.put("error", result.getErrorMessage());
            }
            return writeJson(response);
        }

        private Map<String, Object> parseToolArguments(String input) {
            if (input == null || input.isBlank()) {
                return Map.of();
            }
            try {
                return objectMapper.readValue(input, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Cannot parse tool arguments for {}: {}", tool.getName(), e.getMessage());
                return Map.of();
            }
        }

        private String writeJson(Map<String, Object> value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
    }

    private List<Media> buildImageMedia(List<AttachmentRequest> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<Media> media = new ArrayList<>();
        for (AttachmentRequest a : attachments) {
            if (a == null || a.getType() == null || !a.getType().toLowerCase().startsWith("image")) {
                continue;
            }
            byte[] bytes = imageBytes(a);
            if (bytes == null || bytes.length == 0) {
                continue;
            }
            MimeType mimeType = a.getType().contains("/")
                    ? MimeTypeUtils.parseMimeType(a.getType())
                    : MimeTypeUtils.IMAGE_JPEG;
            media.add(Media.builder()
                    .mimeType(mimeType)
                    .data(new NamedByteArrayResource(bytes, a.getName()))
                    .name(a.getName())
                    .build());
        }
        return media;
    }

    private byte[] imageBytes(AttachmentRequest a) {
        try {
            if (a.getUrl() != null && a.getUrl().startsWith("data:")) {
                int commaIdx = a.getUrl().indexOf(',');
                if (commaIdx > 0) {
                    return Base64.getDecoder().decode(a.getUrl().substring(commaIdx + 1));
                }
            } else if (a.getUrl() != null && a.getUrl().startsWith("http")) {
                return fetchBytes(a.getUrl());
            }
        } catch (Exception e) {
            log.debug("imageBytes failed: {}", e.getMessage());
        }
        return null;
    }

    private byte[] fetchBytes(String url) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return resp.body();
            }
        } catch (Exception e) {
            log.debug("fetchBytes failed: {}", e.getMessage());
        }
        return null;
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
