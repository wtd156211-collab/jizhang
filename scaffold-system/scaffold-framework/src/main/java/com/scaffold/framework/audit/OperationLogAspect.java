package com.scaffold.framework.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Aspect
@Component
public class OperationLogAspect {

    private static final int MAX_PARAMS_LENGTH = 4_000;
    private static final int MAX_ERROR_LENGTH = 4_000;

    private final OperationLogRecorder recorder;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(OperationLogRecorder recorder, ObjectMapper objectMapper) {
        this.recorder = recorder;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(operLog)")
    public Object record(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            record(joinPoint, operLog, start, 1, null);
            return result;
        } catch (Throwable error) {
            record(joinPoint, operLog, start, 0, error);
            throw error;
        }
    }

    private void record(ProceedingJoinPoint joinPoint, OperLog operLog, long start, int result, Throwable error) {
        HttpServletRequest request = currentRequest();
        recorder.record(new OperationLogEntry(
                operLog.module(),
                operLog.action(),
                request == null ? null : request.getMethod(),
                request == null ? null : request.getRequestURI(),
                truncate(params(joinPoint.getArgs()), MAX_PARAMS_LENGTH),
                currentUsername(),
                request == null ? null : clientIp(request),
                System.currentTimeMillis() - start,
                result,
                truncate(error == null ? null : error.getMessage(), MAX_ERROR_LENGTH)
        ));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String params(Object[] args) {
        try {
            ArrayNode root = objectMapper.createArrayNode();
            for (Object arg : args) root.add(argumentNode(arg));
            maskSensitiveFields(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException | IllegalArgumentException error) {
            return "[]";
        }
    }

    private JsonNode argumentNode(Object arg) {
        if (arg instanceof MultipartFile file) {
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("originalName", file.getOriginalFilename());
            metadata.put("mimeType", file.getContentType());
            metadata.put("fileSize", file.getSize());
            return metadata;
        }
        if (arg instanceof MultipartFile[] files) {
            ArrayNode metadata = objectMapper.createArrayNode();
            for (MultipartFile file : files) metadata.add(argumentNode(file));
            return metadata;
        }
        return objectMapper.valueToTree(arg);
    }

    private void maskSensitiveFields(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            JsonNode key = objectNode.has("key") ? objectNode.get("key") : objectNode.get("configKey");
            if (key != null && key.isTextual() && (isSensitive(key.asText())
                    || key.asText().trim().toLowerCase(Locale.ROOT).startsWith("bookkeeping.ocr."))) {
                if (objectNode.has("value")) objectNode.put("value", "******");
                if (objectNode.has("configValue")) objectNode.put("configValue", "******");
            }
            objectNode.fieldNames().forEachRemaining(field -> {
                JsonNode child = objectNode.get(field);
                if (isSensitive(field)) {
                    objectNode.put(field, "******");
                } else {
                    maskSensitiveFields(child);
                }
            });
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::maskSensitiveFields);
        }
    }

    private boolean isSensitive(String field) {
        String lower = field.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("authorization")
                || lower.contains("apikey");
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : String.valueOf(authentication.getPrincipal());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
