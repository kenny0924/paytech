package com.paytech.ledger.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 自动记账规则集及其版本摘要。 */
@Getter
@Setter
public class RuleBundleResponseBody {
    /** 规则集主键。 */
    private String bundleId;

    /** 规则集代码。 */
    private String bundleCode;

    /** 规则集状态：DRAFT、ACTIVE、INACTIVE。 */
    private String status;

    /**
     * 当前生效版本号
     * <li>尚无生效版本时为空。</li>
     */
    private String currentBundleVer;

    /** 全部版本摘要，按版本号降序。 */
    private List<Version> versions;

    /** 版本摘要。 */
    @Getter
    @Setter
    public static class Version {
        /** 版本主键。 */
        private String versionId;

        /** 版本号。 */
        private String bundleVer;

        /** 版本状态。 */
        private String status;

        /** 开始适用时间（UTC）。 */
        private Instant effectiveFrom;

        /**
         * 停止适用时间（UTC）
         * <li>未失效时为空。</li>
         */
        private Instant effectiveTo;
    }
}
