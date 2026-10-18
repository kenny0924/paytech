package com.paytech.ledger.domain.view;

import java.time.Instant;
import java.util.List;

import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.core.enums.RuleVersionStatus;

import lombok.Getter;
import lombok.Setter;

/** 规则集及其全部版本摘要的组合结果。 */
@Getter
@Setter
public class RuleBundleView {
    /** 规则集主键。 */
    private Long bundleId;

    /** 规则集代码。 */
    private String bundleCode;

    /** 规则集状态。 */
    private ReferenceDataStatus status;

    /**
     * 当前生效版本号
     * <li>尚无当前版本时为空。</li>
     */
    private Long currentBundleVer;

    /** 全部版本摘要，按版本号降序。 */
    private List<Version> versions;

    /** 由规则集与其版本（版本号降序）组装。 */
    public static RuleBundleView of(AccountingRuleBundle bundle, List<AccountingRuleBundleVer> versions) {
        RuleBundleView view = new RuleBundleView();
        view.setBundleId(bundle.getId());
        view.setBundleCode(bundle.getBundleCode());
        view.setStatus(bundle.getStatus());
        view.setCurrentBundleVer(versions.stream().filter(ver -> ver.getId().equals(bundle.getCurrentVerId()))
            .map(AccountingRuleBundleVer::getBundleVer).findFirst().orElse(null));
        view.setVersions(versions.stream().map(Version::of).toList());
        return view;
    }

    /** 版本摘要。 */
    @Getter
    @Setter
    public static class Version {
        /** 版本主键。 */
        private Long versionId;

        /** 版本号。 */
        private Long bundleVer;

        /** 版本状态。 */
        private RuleVersionStatus status;

        /** 开始适用时间。 */
        private Instant effectiveFrom;

        /**
         * 停止适用时间
         * <li>未失效时为空。</li>
         */
        private Instant effectiveTo;

        /** 由版本组装摘要。 */
        static Version of(AccountingRuleBundleVer ver) {
            Version version = new Version();
            version.setVersionId(ver.getId());
            version.setBundleVer(ver.getBundleVer());
            version.setStatus(ver.getStatus());
            version.setEffectiveFrom(ver.getEffectiveFrom());
            version.setEffectiveTo(ver.getEffectiveTo());
            return version;
        }
    }
}
