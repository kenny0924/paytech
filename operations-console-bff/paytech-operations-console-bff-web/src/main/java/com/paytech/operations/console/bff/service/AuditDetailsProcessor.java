package com.paytech.operations.console.bff.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.model.AuditModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/** 按历史属性顺序组装变更详情，保留同一字段的全部值与已隐藏的 null。 @author Codex */
@Component
@AllArgsConstructor
public final class AuditDetailsProcessor {
    private static final String BEFORE_PREFIX = "before.";
    private static final String AFTER_PREFIX = "after.";
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("CODE", "编码"), Map.entry("DISPLAY_NAME", "名称"), Map.entry("DESCRIPTION", "说明"), Map.entry("STATUS", "状态"),
        Map.entry("SECURITY_STATUS", "安全状态"),
        Map.entry("roleCode", "角色编码"), Map.entry("roleName", "角色名称"), Map.entry("roleId", "角色 ID"),
        Map.entry("roleCount", "角色数量"), Map.entry("permissionId", "权限 ID"), Map.entry("permissionCount", "权限数量"),
        Map.entry("version", "版本"), Map.entry("status", "状态"), Map.entry("accountStatus", "账号状态"),
        Map.entry("accountVersion", "账号版本"), Map.entry("memberId", "成员 ID"), Map.entry("memberStatus", "成员状态"),
        Map.entry("memberVersion", "成员版本"), Map.entry("authEpoch", "认证版本"));

    private final PlatformOperationsClient client;

    public AuditEventResponseBody details(OperatorContext operator, String auditEventId) {
        var details = client.auditEvent(operator, auditEventId);
        requireValidResponse(auditEventId.equals(details.event().auditEventId()));
        Map<String, ChangeValues> changes = new LinkedHashMap<>();
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        for (AuditAttribute attribute : orderedAttributes(details.attributes())) {
            appendAttribute(attribute, changes, attributes);
        }
        return new AuditEventResponseBody(details.event(), changes.entrySet().stream()
            .map(entry -> new AuditChange(entry.getKey(), label(entry.getKey()),
                entry.getValue().before.stream().toList(), entry.getValue().after.stream().toList())).toList(),
            attributes.entrySet().stream()
                .map(entry -> new AuditProperty(entry.getKey(), label(entry.getKey()), entry.getValue().stream().toList()))
                .toList());
    }

    private List<AuditAttribute> orderedAttributes(List<AuditAttribute> attributes) {
        Set<Integer> numbers = new HashSet<>();
        for (AuditAttribute attribute : attributes) {
            requireValidResponse(numbers.add(attribute.number()));
        }
        return attributes.stream().sorted(Comparator.comparingInt(AuditAttribute::number)).toList();
    }

    private void appendAttribute(AuditAttribute attribute, Map<String, ChangeValues> changes,
        Map<String, List<String>> attributes) {
        String key = attribute.key();
        if (key.startsWith(BEFORE_PREFIX) || key.startsWith(AFTER_PREFIX)) {
            boolean before = key.startsWith(BEFORE_PREFIX);
            String field = key.substring(before ? BEFORE_PREFIX.length() : AFTER_PREFIX.length());
            requireValidResponse(!field.isBlank());
            ChangeValues values = changes.computeIfAbsent(field, ignored -> new ChangeValues());
            (before ? values.before : values.after).add(attribute.value());
            return;
        }
        if (!key.equals("reason")) {
            attributes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(attribute.value());
        }
    }

    private static String label(String key) {
        return LABELS.getOrDefault(key, key);
    }

    private static void requireValidResponse(boolean condition) {
        if (!condition) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }

    private static final class ChangeValues {
        private final List<String> before = new ArrayList<>();
        private final List<String> after = new ArrayList<>();
    }
}
