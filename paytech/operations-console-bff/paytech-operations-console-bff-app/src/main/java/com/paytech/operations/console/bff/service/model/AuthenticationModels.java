package com.paytech.operations.console.bff.service.model;

import java.util.regex.Pattern;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BFF 认证用例模型；与 Platform 传输 DTO 隔离，敏感对象不输出内容。
 *
 * @author Codex
 */
public final class AuthenticationModels {
    private static final Pattern REFRESH_TOKEN = Pattern.compile("[A-Za-z0-9_-]{12,128}\\.[A-Za-z0-9_-]{43}");

    private AuthenticationModels() {
    }

    /** 校验 Platform 签发的 Refresh 凭据格式。 */
    public static boolean isValidRefreshToken(String value) {
        return value != null && REFRESH_TOKEN.matcher(value).matches();
    }

    public record LoginRequestBody(@NotBlank @Size(max = 320) String loginIdentifier,
                               @NotBlank @Size(max = 512) String password) {
        @Override
        public String toString() {
            return "LoginRequestBody[REDACTED]";
        }
    }

    public record RefreshRequestBody(@NotBlank @Size(max = 512) String refreshToken) {
        @Override
        public String toString() {
            return "RefreshRequestBody[REDACTED]";
        }
    }

    public record ChangePasswordRequestBody(@NotBlank @Size(max = 512) String currentPassword,
                                        @NotBlank @Size(max = 512) String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequestBody[REDACTED]";
        }
    }

    public record FirstAdministratorRequestBody(@NotBlank @Size(max = 320) String email,
                                           @NotBlank @Size(max = 512) String initialPassword) {
        @Override
        public String toString() {
            return "FirstAdministratorRequestBody[REDACTED]";
        }
    }

    /** 登录和刷新返回的一组凭据；调用方在成功后同时替换两种 Token。 */
    public record TokenGrantResponseBody(String accessToken, String tokenType, long expiresIn,
                             String refreshToken, long refreshExpiresIn,
                             boolean passwordChangeRequired, boolean bootstrapAdministratorRequired) {
        @Override
        public String toString() {
            return "TokenGrantResponseBody[REDACTED]";
        }
    }

    public record SessionContextResponseBody(String accountId, String sessionId, String accountType, String emailMasked,
                                 String accountStatus, String tenantId, String memberId,
                                 boolean passwordChangeRequired, boolean bootstrapAdministratorRequired,
                                 String integrationMode) {
    }

    public record AccountCreatedResponseBody(String accountId, String memberId, int version) {
    }
}
