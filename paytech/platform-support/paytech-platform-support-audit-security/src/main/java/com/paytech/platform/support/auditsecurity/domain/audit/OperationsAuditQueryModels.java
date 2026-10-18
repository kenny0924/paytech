package com.paytech.platform.support.auditsecurity.domain.audit;

import java.time.Instant;
import java.util.List;

/** Audit capability's authorized-query input and immutable projections. */
public final class OperationsAuditQueryModels {
    private OperationsAuditQueryModels() {}

    public record AuditSearch(Instant occurredFrom, Instant occurredTo, String actorId,
        String actionCode, String result, String requestId, String resourceType, String resourceId) {}

    public record AuditEvent(String auditEventId, String actorId, String actionCode, String resourceType,
        String resourceId, String result, String requestId, Instant occurredTime, String reason) {}

    public record AuditAttribute(int number, String key, String value) {}

    public record AuditDetails(AuditEvent event, List<AuditAttribute> attributes) {
        public AuditDetails {
            attributes = List.copyOf(attributes);
        }
    }

    public record AuditEventPage(List<AuditEvent> items, long total) {
        public AuditEventPage {
            items = List.copyOf(items);
        }
    }
}
