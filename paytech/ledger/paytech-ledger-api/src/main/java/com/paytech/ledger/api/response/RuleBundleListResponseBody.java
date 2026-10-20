package com.paytech.ledger.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 全部自动记账规则集。 */
@Getter
@Setter
public class RuleBundleListResponseBody {
    /** 按规则集代码升序的规则集。 */
    private List<Bundle> items;

    /** 规则集及其当前生效版本。 */
    @Getter
    @Setter
    public static class Bundle {
        /** 规则集代码。 */
        private String bundleCode;

        /** 规则集状态：DRAFT、ACTIVE、INACTIVE。 */
        private String status;

        /**
         * 当前生效版本号
         * <li>尚无生效版本时为空。</li>
         */
        private String currentBundleVer;

        /**
         * 当前生效版本的开始适用时间（UTC）
         * <li>尚无生效版本时为空。</li>
         */
        private Instant currentEffectiveFrom;
    }
}
