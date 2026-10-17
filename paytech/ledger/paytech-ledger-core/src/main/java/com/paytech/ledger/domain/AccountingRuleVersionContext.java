package com.paytech.ledger.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;
import com.paytech.ledger.domain.view.RuleVersionView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次规则版本写入：规则集、目标版本及其规则行；批准生效时附带被替代的原当前版本。 */
@Getter
@AllArgsConstructor
public class AccountingRuleVersionContext {
    /** 规则集；切换当前版本后由 Repository 同步。 */
    private final AccountingRuleBundle bundle;

    /** 目标版本；状态迁移后由 Repository 同步。 */
    private final AccountingRuleBundleVer ver;

    /** 目标版本的规则行，按指令类型、行号升序。 */
    private final List<AccountingRuleLine> lines;

    /**
     * 被替代的原当前版本
     * <li>仅批准且已到生效时间、规则集已有当前版本时非空。</li>
     */
    private final AccountingRuleBundleVer currentVer;

    /** 不涉及当前版本切换的写入。 */
    public AccountingRuleVersionContext(AccountingRuleBundle bundle, AccountingRuleBundleVer ver,
        List<AccountingRuleLine> lines) {
        this(bundle, ver, lines, null);
    }

    /** 批准时刻是否已到目标版本的生效时间。 */
    public boolean effectiveAt(Instant time) {
        return !ver.getEffectiveFrom().isAfter(time);
    }

    /** 目标版本详情。 */
    public RuleVersionView toView() {
        return RuleVersionView.of(bundle.getBundleCode(), ver, lines);
    }
}
