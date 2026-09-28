package com.paytech.foundation.xxl.core.autoconfigure;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.xxl.core.error.XxlJobErrorCode;

/**
 * XXL-Job Executor 配置。
 *
 * @author Codex
 */
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    private final Admin admin = new Admin();
    private final Executor executor = new Executor();

    public Admin getAdmin() {
        return admin;
    }

    public Executor getExecutor() {
        return executor;
    }

    /**
     * 校验启用 Executor 时必须具备的完整、安全配置。
     */
    public void validateEnabledConfiguration() {
        requireText("admin.addresses", admin.addresses, 1024);
        validateAdminAddresses(admin.addresses);
        requireStrongToken(admin.accessToken);
        if (admin.timeout < 1 || admin.timeout > 10) {
            throw configurationError("admin.timeout必须在1到10秒之间");
        }

        requireText("executor.app-name", executor.appName, 64);
        if (!executor.appName.matches("[a-z][a-z0-9-]{1,63}")) {
            throw configurationError("executor.app-name必须使用小写kebab-case");
        }
        if (executor.port < 1 || executor.port > 65535) {
            throw configurationError("executor.port必须在1到65535之间且由每个服务显式配置");
        }
        requireText("executor.log-path", executor.logPath, 512);
        if (executor.logRetentionDays < 3 || executor.logRetentionDays > 3650) {
            throw configurationError("executor.log-retention-days必须在3到3650天之间");
        }
        validateExcludedPackages(executor.excludedPackages);
        validateOptionalAddress("executor.address", executor.address);
        validateOptionalText("executor.ip", executor.ip, 255);
    }

    private void validateAdminAddresses(String addresses) {
        for (String address : addresses.split(",", -1)) {
            validateAddress("admin.addresses", address);
        }
    }

    private void validateOptionalAddress(String property, String address) {
        if (address == null || address.isBlank()) {
            return;
        }
        validateAddress(property, address);
    }

    private void validateAddress(String property, String address) {
        String normalizedAddress = requireText(property, address, 512);
        URI uri;
        try {
            uri = URI.create(normalizedAddress);
        } catch (IllegalArgumentException exception) {
            throw configurationError(property + "不是有效的HTTP(S)地址");
        }
        boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        boolean rootPath = uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath());
        if (!supportedScheme || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
            || uri.getFragment() != null || !rootPath) {
            throw configurationError(property + "必须是无凭据、Query、Fragment和Context Path的HTTP(S)根地址");
        }
    }

    private void validateExcludedPackages(String excludedPackages) {
        for (String excludedPackage : excludedPackages.split(",", -1)) {
            String normalizedPackage = requireText("executor.excluded-packages", excludedPackage, 512);
            if (normalizedPackage.chars().anyMatch(Character::isWhitespace)) {
                throw configurationError("executor.excluded-packages不能包含空白字符");
            }
        }
    }

    private void validateOptionalText(String property, String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalizedValue = requireText(property, value, maximumLength);
        if (normalizedValue.chars().anyMatch(Character::isWhitespace)) {
            throw configurationError(property + "不能包含空白字符");
        }
    }

    private void requireStrongToken(String accessToken) {
        String normalizedToken = requireText("admin.access-token", accessToken, 256);
        if (normalizedToken.length() < 32 || normalizedToken.chars().anyMatch(Character::isWhitespace)) {
            throw configurationError("admin.access-token必须至少32个字符且不能包含空白字符");
        }
    }

    private String requireText(String property, String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw configurationError(property + "不能为空");
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maximumLength) {
            throw configurationError(property + "不能超过" + maximumLength + "个字符");
        }
        return normalizedValue;
    }

    private FailException configurationError(String message) {
        return new FailException(XxlJobErrorCode.CONFIGURATION_INVALID, "xxl.job." + message);
    }

    /**
     * 调度中心连接配置。
     *
     * @author Codex
     */
    public static class Admin {

        private String addresses;
        private String accessToken;
        private int timeout = 3;

        public String getAddresses() {
            return addresses;
        }

        public void setAddresses(String addresses) {
            this.addresses = addresses;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * 当前服务 Executor 配置。
     *
     * @author Codex
     */
    public static class Executor {

        private boolean enabled;
        private String appName;
        private String address;
        private String ip;
        private int port;
        private String logPath = "./logs/xxl-job";
        private int logRetentionDays = 30;
        private String excludedPackages = "org.springframework.,spring.";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        /**
         * 获取向 Admin 注册的 IP。该配置不会限制 XXL 内嵌服务器的监听网卡。
         *
         * @return 注册 IP
         */
        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getLogPath() {
            return logPath;
        }

        public void setLogPath(String logPath) {
            this.logPath = logPath;
        }

        public int getLogRetentionDays() {
            return logRetentionDays;
        }

        public void setLogRetentionDays(int logRetentionDays) {
            this.logRetentionDays = logRetentionDays;
        }

        public String getExcludedPackages() {
            return excludedPackages;
        }

        public void setExcludedPackages(String excludedPackages) {
            this.excludedPackages = excludedPackages;
        }
    }
}
