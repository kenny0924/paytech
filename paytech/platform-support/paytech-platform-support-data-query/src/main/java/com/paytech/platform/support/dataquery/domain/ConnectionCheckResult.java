package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import com.paytech.platform.support.dataquery.core.enums.DataSourceCheckResult;

import lombok.Getter;

/** 连接检查结果：连通性、账号授权原文与不通过原因。 */
@Getter
public final class ConnectionCheckResult {

    private static final int MAX_MESSAGE_LENGTH = 512;

    /** 是否建立连接并成功执行 SELECT 1。 */
    private final boolean connected;

    /** 授权原文，逐行；未连通时为空。 */
    private final List<String> grants;

    /** 不通过原因，中文；通过时为空。 */
    private final List<String> violations;

    /**
     * 构造。
     *
     * @param connected 是否连通
     * @param grants 授权原文
     * @param violations 不通过原因
     */
    public ConnectionCheckResult(boolean connected, List<String> grants, List<String> violations) {
        this.connected = connected;
        this.grants = List.copyOf(grants);
        this.violations = List.copyOf(violations);
    }

    /**
     * 连接失败的结果。
     *
     * @param reason 原因，不含密码
     * @return 结果
     */
    public static ConnectionCheckResult unreachable(String reason) {
        return new ConnectionCheckResult(false, List.of(), List.of("连接失败: " + reason));
    }

    /**
     * 是否全部通过。
     *
     * @return 连通且没有违规
     */
    public boolean passed() {
        return connected && violations.isEmpty();
    }

    /**
     * 账号权限是否通过。
     *
     * @return 连通且没有违规
     */
    public boolean privilegesPassed() {
        return passed();
    }

    /**
     * 结果编码。
     *
     * @return PASSED 或 FAILED
     */
    public DataSourceCheckResult result() {
        return passed() ? DataSourceCheckResult.PASSED : DataSourceCheckResult.FAILED;
    }

    /**
     * 回写 last_check_message 的说明，超长截断到 512 字符。
     *
     * @return 通过时为“连接与只读权限检查通过”，否则为原因列表
     */
    public String message() {
        String message = passed() ? "连接与只读权限检查通过" : String.join("；", violations);
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH - 1) + "…";
    }
}
