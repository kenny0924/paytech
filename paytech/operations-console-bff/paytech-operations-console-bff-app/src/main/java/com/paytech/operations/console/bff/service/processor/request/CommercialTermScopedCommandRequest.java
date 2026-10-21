package com.paytech.operations.console.bff.service.processor.request;

import java.util.function.Supplier;

import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 按路由范围（合同价目 / 标准价目模板）隔离的条款版本命令的内部请求
 * <li>方案级命令（建版本）给出 planId，版本级命令给出 versionId，二者恰好一个；入口已按正十进制格式校验。</li>
 * <li>command 是校验通过后执行的那一次 commercial 写入。</li>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CommercialTermScopedCommandRequest {

    /** 路由所属范围 */
    @NotNull(message = "路由范围不能为空")
    private final Scope scope;

    /** 方案级命令的方案 ID */
    private final String planId;

    /** 版本级命令的版本 ID */
    private final String versionId;

    /** 归属校验通过后执行的 commercial 写入 */
    @NotNull(message = "命令不能为空")
    private final Supplier<CommercialTermVersionResponseBody> command;

    /**
     * 方案级命令
     *
     * @param scope 路由范围
     * @param planId 方案 ID
     * @param command commercial 写入
     * @return 内部请求
     */
    public static CommercialTermScopedCommandRequest onPlan(Scope scope, String planId,
        Supplier<CommercialTermVersionResponseBody> command) {
        return new CommercialTermScopedCommandRequest(scope, planId, null, command);
    }

    /**
     * 版本级命令
     *
     * @param scope 路由范围
     * @param versionId 版本 ID
     * @param command commercial 写入
     * @return 内部请求
     */
    public static CommercialTermScopedCommandRequest onVersion(Scope scope, String versionId,
        Supplier<CommercialTermVersionResponseBody> command) {
        return new CommercialTermScopedCommandRequest(scope, null, versionId, command);
    }

    /** 方案 ID 与版本 ID 恰好给出一个。 */
    @AssertTrue(message = "方案 ID 与版本 ID 必须恰好给出一个")
    boolean isSingleTarget() {
        return (planId == null) != (versionId == null);
    }

    /** 条款版本命令的路由范围。 */
    public enum Scope {
        /** 合同价目路由：只接受 CLIENT / LOCAL_PARTNER 方案 */
        CONTRACT,

        /** 标准价目模板路由：只接受 PLATFORM_TEMPLATE 方案 */
        TEMPLATE
    }
}
