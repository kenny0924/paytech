package com.paytech.finance.accounting.core.dal.dataobject;

import java.time.Instant;

import com.paytech.finance.accounting.core.enums.RuleVersionStatus;

import lombok.Getter;
import lombok.Setter;

/** t_accounting_rule_bundle_ver：一版自动记账规则的生效区间、发布状态与双人审批结果。 */
@Getter
@Setter
public class AccountingRuleBundleVer {
    /**
     * 自动记账规则版本的 Snowflake 主键
     */
    private Long id;

    /**
     * 所属自动记账规则集主键
     */
    private Long bundleId;

    /**
     * 同一规则集内严格递增的业务版本号
     */
    private Long bundleVer;

    /**
     * 该规则版本开始适用的 UTC 微秒时间
     */
    private Instant effectiveFrom;

    /**
     * 该规则版本停止适用的 UTC 微秒时间
     * <li>可为空，表示尚未失效。</li>
     */
    private Instant effectiveTo;

    /**
     * 审批与生效状态
     */
    private RuleVersionStatus status;

    /**
     * 编制并提交该版本的平台用户主键
     */
    private Long makerId;

    /**
     * 复核该版本的平台用户主键，必须与编制人不同
     * <li>可为空。</li>
     */
    private Long checkerId;

    /**
     * 复核人批准的 UTC 微秒时间
     * <li>可为空；未批准或已驳回时为空。</li>
     */
    private Instant approvedTime;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
