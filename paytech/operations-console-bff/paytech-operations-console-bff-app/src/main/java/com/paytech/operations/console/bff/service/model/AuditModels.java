package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.paytech.operations.console.bff.service.model.ManagementModels.AuditEvent;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 运营审计筛选、历史属性与详情展示模型；不补充当前业务对象的名称。 @author Codex */
public final class AuditModels {
    private AuditModels() {
    }

    public record AuditQueryRequestBody(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
        @Size(max = 128) String actorId, @Size(max = 64) String actionCode,
        @Pattern(regexp = "SUCCEEDED|DENIED|FAILED") String result, @Size(max = 128) String requestId,
        @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String resourceType, @Size(max = 128) String resourceId) {
        public AuditQueryRequestBody {
            actorId = optionalText(actorId);
            actionCode = optionalText(actionCode);
            result = optionalText(result);
            requestId = optionalText(requestId);
            resourceType = optionalText(resourceType);
            resourceId = optionalText(resourceId);
        }

        /** 只在指定对象类型后允许对象身份过滤，避免跨类型混查。 */
        @AssertTrue(message = "对象 ID 必须同时指定对象类型")
        public boolean isResourceFilterValid() { return resourceId == null || resourceType != null; }

        @AssertTrue(message = "审计开始时间必须早于结束时间")
        public boolean isTimeRangeValid() {
            return occurredFrom == null || occurredTo == null || occurredFrom.isBefore(occurredTo);
        }

        private static String optionalText(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }

    /** 上游历史事实的本地映射，只在应用层参与展示组装。 */
    public record AuditEventDetails(AuditEvent event, List<AuditAttribute> attributes) {
    }

    public record AuditAttribute(int number, String key, String value) {
    }

    public record AuditEventResponseBody(AuditEvent event, List<AuditChange> changes, List<AuditProperty> attributes) {
    }

    /** 字段变更；字段名由前端按 {@code key} 翻译，未知 key 原样显示。 */
    public record AuditChange(String key, List<String> before, List<String> after) {
    }

    /** 其他属性；属性名由前端按 {@code key} 翻译，未知 key 原样显示。 */
    public record AuditProperty(String key, List<String> values) {
    }
}
