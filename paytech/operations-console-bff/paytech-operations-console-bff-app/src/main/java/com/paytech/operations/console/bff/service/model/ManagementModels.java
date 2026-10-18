package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 人员和审计的本地请求与页面模型，不暴露远端接口类型。
 *
 * @author Codex
 */
public final class ManagementModels {
    private ManagementModels() {
    }

    public record CreateAccountRequestBody(@NotBlank @Size(max = 320) String email,
                                       @NotBlank @Size(max = 512) String initialPassword,
                                       @NotEmpty @Size(max = 20) List<String> roleIds,
                                       @NotBlank @Size(max = 500) String reason) {
        @Override
        public String toString() {
            return "CreateAccountRequestBody[REDACTED]";
        }
    }

    public record DisableAccountRequestBody(@Positive int expectedVersion,
                                        @NotBlank @Size(max = 500) String reason) {
    }

    public record EnableAccountRequestBody(@Positive int expectedVersion,
                                       @NotBlank @Size(max = 500) String reason) {
    }

    public record DeleteAccountRequestBody(@Positive int expectedVersion, @Positive int expectedMemberVersion,
                                        @NotBlank @Size(max = 500) String reason) {
    }

    public record AssignRolesRequestBody(@Positive int expectedMemberVersion,
                                     @NotEmpty @Size(max = 20) List<String> roleIds,
                                     @NotBlank @Size(max = 500) String reason) {
    }

    public record AccountResponseBody(String accountId, String emailMasked, String status, boolean passwordChangeRequired,
                                 Instant lockedUntil, int version, String memberId, int memberVersion,
                                 List<RoleSummary> roles) {
    }

    public record RoleSummary(String roleId, String roleCode, String roleName, boolean systemRole) {
    }

    public record AuditEvent(String auditEventId, String actorId, String actionCode, String resourceType,
                             String resourceId, String result, String requestId, Instant occurredTime, String reason) {
    }
}
