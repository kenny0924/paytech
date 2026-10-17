package com.paytech.platform.support.auditsecurity.domain.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.platform.support.auditsecurity.domain.enums.AuditActorType;

/** 只携带有界安全标量；构造异常须由调用方采集隔离边界处理。 */
public final class AuditModels {
    private AuditModels() {}

    /** 审计主体；人工账号沿用两参构造，内部服务调用方显式声明 WORKLOAD。 */
    public record Identity(String actorId, Long tenantId, AuditActorType actorType) {
        public Identity {
            scalar(actorId, 128);
            require(actorType != null);
        }

        /** 人工账号主体。 */
        public Identity(String actorId, Long tenantId) {
            this(actorId, tenantId, AuditActorType.USER);
        }
    }
    public record Target(String type, String id) {
        public Target {
            scalar(type, 64);
            scalar(id, 128);
        }
    }
    public record Attribute(String key, String value) {
        public Attribute {
            scalar(key, 128);
            scalar(value, 1000);
            require(bytes(value) <= 4096);
        }
    }
    public record Invocation(String action, Target fallback, boolean recordSuccess, String requestId,
        Instant startedAt, Set<String> allowedResourceTypes) {
        public Invocation {
            scalar(action, 64);
            scalar(requestId, 128);
            require(fallback != null && startedAt != null && allowedResourceTypes != null);
            startedAt = startedAt.truncatedTo(ChronoUnit.MICROS);
            require(allowedResourceTypes.size() <= 256);
            allowedResourceTypes.forEach(type -> scalar(type, 64));
            require(allowedResourceTypes.contains(fallback.type()));
            allowedResourceTypes = Set.copyOf(allowedResourceTypes);
        }
        public Invocation(String action, Target fallback, boolean recordSuccess, String requestId,
            Instant startedAt) {
            this(action, fallback, recordSuccess, requestId, startedAt, resourceTypes(fallback));
        }
    }
    public record Fact(Identity identity, Target target, Result result, List<Attribute> attributes,
        Instant occurredAt) {
        public Fact {
            occurredAt = microseconds(occurredAt);
            checkBudget(identity, target, result, attributes, occurredAt, 0);
            attributes = List.copyOf(attributes);
        }
    }
    public record Event(Identity identity, String action, Target target, Result result, List<Attribute> attributes,
        String requestId, Instant occurredAt) {
        public Event {
            occurredAt = microseconds(occurredAt);
            scalar(action, 64);
            scalar(requestId, 128);
            checkBudget(identity, target, result, attributes, occurredAt, bytes(action) + bytes(requestId));
            attributes = List.copyOf(attributes);
        }
    }
    public record StatusVersion(String status, int version) {
        public StatusVersion {
            scalar(status, 64);
            require(version >= 0);
        }
    }
    public record GatewayChange(Target target, String sessionId, StatusVersion before, StatusVersion after,
        String reason) {
        public GatewayChange {
            require(target != null && before != null && after != null);
            scalar(sessionId, 128);
            scalar(reason, 1000);
        }
    }

    public static Optional<Fact> freeze(Fact fact) {
        if (fact == null) return Optional.empty();
        try {
            return Optional.of(new Fact(fact.identity(), fact.target(), fact.result(), fact.attributes(), fact.occurredAt()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static Optional<Event> freeze(Event event) {
        if (event == null) return Optional.empty();
        try {
            return Optional.of(new Event(event.identity(), event.action(), event.target(), event.result(),
                event.attributes(), event.requestId(), event.occurredAt()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Set<String> resourceTypes(Target fallback) {
        require(fallback != null);
        return Set.of(fallback.type());
    }

    private static Instant microseconds(Instant instant) {
        require(instant != null);
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    private static void checkBudget(Identity identity, Target target, Result result, List<Attribute> attributes,
        Instant occurredAt, int extraBytes) {
        require(identity != null && target != null && result != null && occurredAt != null && attributes != null);
        require(attributes.size() <= 256);
        int size = extraBytes + bytes(identity.actorId()) + bytes(target.type()) + bytes(target.id())
            + bytes(result.getCode()) + bytes(occurredAt.toString());
        if (identity.tenantId() != null) {
            size += bytes(identity.tenantId().toString());
        }
        for (Attribute attribute : attributes) {
            require(attribute != null);
            size += bytes(attribute.key()) + bytes(attribute.value());
            require(size <= 65536);
        }
        require(size <= 65536);
    }

    private static void scalar(String value, int maxCharacters) {
        require(value != null && value.length() <= maxCharacters * 2);
        require(value.codePointCount(0, value.length()) <= maxCharacters);
        bytes(value);
    }

    /** 增量计算 UTF-8 预算，不为超长输入分配编码数组；拒绝不可持久化的孤立代理项。 */
    private static int bytes(String value) {
        int size = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                require(index + 1 < value.length() && Character.isLowSurrogate(value.charAt(index + 1)));
                index++;
                size += 4;
            } else {
                require(!Character.isLowSurrogate(character));
                size += character <= 0x7f ? 1 : character <= 0x7ff ? 2 : 3;
            }
        }
        return size;
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("审计安全值无效或超过预算");
    }

    public enum Result implements CodeEnum {
        SUCCEEDED("SUCCEEDED"),

        DENIED("DENIED"),

        FAILED("FAILED");

        private final String code;
        Result(String code) { this.code = code; }
        @Override public String getCode() { return code; }
    }

    public enum CallOutcome {
        RETURNED,

        DENIED,

        FAILED
    }

    public enum TransactionOutcome {
        NOT_STARTED,

        ACTIVE,

        COMMITTED,

        ROLLED_BACK,

        UNKNOWN
    }
}
