package com.paytech.operations.console.bff.service.processor;

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
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditAttribute;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditChange;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditEventResponseBody;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditProperty;
import com.paytech.operations.console.bff.service.processor.request.AuditEventDetailsRequest;

import lombok.AllArgsConstructor;

/**
 * 审计事件详情页：读取 Platform 审计事件，按历史属性序号组装字段变更与其他属性
 * <li>{@code before.} / {@code after.} 前缀的属性按字段合并为变更，同一字段的全部值与已隐藏的 null 都保留。</li>
 * <li>{@code reason} 已在事件本身展示，不重复列为属性；其余属性按 Key 合并全部取值。</li>
 * <li>回执事件 ID 不一致、属性序号重复或变更字段名为空，均视为上游无效响应，不返回部分详情。</li>
 * <li>只返回字段与属性的 Key，展示名称由前端翻译。</li>
 *
 * @author Codex
 */
@Component
@AllArgsConstructor
public class AuditEventDetailsProcessor extends AbstractProcessor<AuditEventDetailsRequest, AuditEventResponseBody> {
    private static final String BEFORE_PREFIX = "before.";
    private static final String AFTER_PREFIX = "after.";
    private static final String REASON_KEY = "reason";

    private final PlatformOperationsClient client;

    @Override
    protected AuditEventResponseBody processRequest(AuditEventDetailsRequest request) {
        var details = client.auditEvent(request.getOperator(), request.getAuditEventId());
        requireValidResponse(request.getAuditEventId().equals(details.event().auditEventId()));
        Map<String, ChangeValues> changes = new LinkedHashMap<>();
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        for (AuditAttribute attribute : orderedAttributes(details.attributes())) {
            appendAttribute(attribute, changes, attributes);
        }
        return new AuditEventResponseBody(details.event(), changeItems(changes), propertyItems(attributes));
    }

    /** 按序号排序属性；序号重复属于上游无效响应。 */
    List<AuditAttribute> orderedAttributes(List<AuditAttribute> attributes) {
        Set<Integer> numbers = new HashSet<>();
        for (AuditAttribute attribute : attributes) {
            requireValidResponse(numbers.add(attribute.number()));
        }
        return attributes.stream().sorted(Comparator.comparingInt(AuditAttribute::number)).toList();
    }

    /** 把一个属性归入字段变更或普通属性；变更前缀后的字段名不能为空。 */
    void appendAttribute(AuditAttribute attribute, Map<String, ChangeValues> changes,
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
        if (!key.equals(REASON_KEY)) {
            attributes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(attribute.value());
        }
    }

    /** 字段变更转为页面项，保持字段首次出现的顺序；取值中的 null 原样保留。 */
    static List<AuditChange> changeItems(Map<String, ChangeValues> changes) {
        return changes.entrySet().stream().map(entry -> new AuditChange(entry.getKey(),
            entry.getValue().before.stream().toList(), entry.getValue().after.stream().toList())).toList();
    }

    /** 普通属性转为页面项，保持 Key 首次出现的顺序。 */
    static List<AuditProperty> propertyItems(Map<String, List<String>> attributes) {
        return attributes.entrySet().stream()
            .map(entry -> new AuditProperty(entry.getKey(), entry.getValue().stream().toList()))
            .toList();
    }

    /** 上游回执不满足合同时整页失败。 */
    static void requireValidResponse(boolean condition) {
        if (!condition) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }

    /** 同一字段的变更前、变更后取值，按属性序号追加。 */
    static final class ChangeValues {
        private final List<String> before = new ArrayList<>();
        private final List<String> after = new ArrayList<>();
    }
}
