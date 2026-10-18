package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;
import com.paytech.platform.support.accesscontrol.domain.enums.LoginFailureReason;

/** 认证 Owner 的命令与结果；敏感值只跨内部调用边界，不进入普通日志。 */
public final class OperationsAuthenticationModels {

    private OperationsAuthenticationModels() {}

    public record LoginCommand(String loginIdentifier, String password) {
        @Override
        public String toString() {
            return "LoginCommand[REDACTED]";
        }
    }

    public record RefreshCommand(String refreshToken) {
        @Override
        public String toString() {
            return "RefreshCommand[REDACTED]";
        }
    }

    public record ChangePasswordCommand(String currentPassword, String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordCommand[REDACTED]";
        }
    }

    public record AccountCreatedResult(long accountId, long memberId, int version) {
    }

    public record TokenResult(String accessToken, String tokenType, long expiresIn, String refreshToken,
        long refreshExpiresIn, boolean passwordChangeRequired, boolean bootstrapAdministratorRequired) {
        @Override
        public String toString() {
            return "TokenResult[REDACTED]";
        }
    }

    public record LoginResolution(Long accountId, String resourceKey) {
    }

    public record PreparedTokens(OperationsRefresh refresh, TokenResult response) {
        @Override
        public String toString() {
            return "PreparedTokens[REDACTED]";
        }
    }

    public record PreparedLogin(OperationsAccount account, OperationsPassword password, OperationsMember member,
        OperationsSession session, PreparedTokens tokens, LoginFailureReason failureReason, Instant preparedAt) {
        @Override
        public String toString() {
            return "PreparedLogin[REDACTED]";
        }
    }

    public record RefreshResolution(Long sessionId, String resourceKey) {
    }

    public record PreparedRefresh(OperationsRefresh refresh, OperationsSession session, OperationsAccount account,
        OperationsPassword password, OperationsMember member, PreparedTokens tokens, Instant preparedAt) {
        @Override
        public String toString() {
            return "PreparedRefresh[REDACTED]";
        }
    }

    public record PreparedLogout(long accountId, OperationsSession session, OperationsRefresh refresh,
        Instant preparedAt) {
        @Override
        public String toString() {
            return "PreparedLogout[REDACTED]";
        }
    }

    public record PreparedPasswordChange(OperationsAccount account, OperationsPassword password,
        OperationsSession session, String nextVerifier, Instant preparedAt) {
        @Override
        public String toString() {
            return "PreparedPasswordChange[REDACTED]";
        }
    }

    public record PreparedHumanCredentials(String canonicalEmail, String vaultToken, String maskedEmail,
        String verifier) {
        @Override
        public String toString() {
            return "PreparedHumanCredentials[REDACTED]";
        }
    }

}
