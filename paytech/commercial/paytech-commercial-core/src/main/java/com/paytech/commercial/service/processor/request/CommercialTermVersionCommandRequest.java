package com.paytech.commercial.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 版本命令（提交、批准、驳回、排期、激活）的内部请求
 * <li>系统命令（定时激活）expectedChangeVersion 与 operatorAccountId 为空：锁内读取当前根版本，操作人记 0。</li>
 */
@Getter
@AllArgsConstructor
public class CommercialTermVersionCommandRequest {
    /** 版本 ID */
    @Positive
    private final long versionId;

    /** 方案根期望版本；系统命令为空 */
    @Positive
    private final Long expectedChangeVersion;

    /** 操作人账号 ID；系统命令为空 */
    @PositiveOrZero
    private final Long operatorAccountId;

    /** 排期写入的生效起点；其他命令为空 */
    private final Instant effectiveFrom;

    /**
     * 运营命令
     *
     * @param versionId 版本 ID
     * @param expectedChangeVersion 方案根期望版本
     * @param operatorAccountId 操作人
     * @return 请求
     */
    public static CommercialTermVersionCommandRequest operator(long versionId, long expectedChangeVersion,
        long operatorAccountId) {
        return new CommercialTermVersionCommandRequest(versionId, expectedChangeVersion, operatorAccountId, null);
    }

    /**
     * 系统命令（定时激活）
     *
     * @param versionId 版本 ID
     * @return 请求
     */
    public static CommercialTermVersionCommandRequest system(long versionId) {
        return new CommercialTermVersionCommandRequest(versionId, null, null, null);
    }

    /** 是否系统命令。 */
    public boolean systemCommand() {
        return operatorAccountId == null;
    }
}
