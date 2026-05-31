package com.notfound.aiservice.agent.tool;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Quản lý toàn bộ tool đã đăng ký (auto-discovery qua Spring bean).
 * Lọc theo cấu hình `ai.agent.enabled-tools` để bật/tắt từng tool.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<Tool> allTools;

    @Value("${ai.agent.enabled-tools:}")
    private String enabledToolsConfig;

    private final Map<String, Tool> registry = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        Set<String> enabledNames = parseEnabledTools(enabledToolsConfig);
        for (Tool tool : allTools) {
            if (enabledNames.isEmpty() || enabledNames.contains(tool.getName())) {
                registry.put(tool.getName(), tool);
                log.info("Registered AI tool: {}", tool.getName());
            } else {
                log.info("Skipped (disabled) AI tool: {}", tool.getName());
            }
        }
    }

    private Set<String> parseEnabledTools(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(Arrays.asList(value.split("\\s*,\\s*")));
    }

    public Collection<Tool> getAll() {
        return registry.values();
    }

    public Optional<Tool> get(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registry.get(name));
    }
}
