package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.core.enums.ChannelCostVersionStatus;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_channel_cost_plan_ver：渠道成本方案版本的生效区间、内容摘要与双人审批结果。 */
@Getter
@Setter
public class ChannelCostPlanVer {
    /**
     * 渠道成本方案版本的 Snowflake 主键
     */
    private Long id;

    /**
     * 所属渠道成本方案主键
     */
    private Long planId;

    /**
     * 同一方案内严格递增的业务版本号
     */
    private Integer costVer;

    /**
     * 开始参与预计成本匹配的 UTC 微秒时间
     */
    private Instant effectiveFrom;

    /**
     * 停止参与预计成本匹配的 UTC 微秒时间
     * <li>可为空，表示长期有效。</li>
     */
    private Instant effectiveTo;

    /**
     * 覆盖版本、全部适用范围和成本组成的 RFC 8785 内容摘要，创建时冻结
     */
    private String contentHash;

    /**
     * 审批与生效状态
     */
    private ChannelCostVersionStatus status;

    /**
     * 编制该版本的平台用户主键
     */
    private Long makerId;

    /**
     * 复核该版本的平台用户主键，必须与编制人不同
     * <li>可为空。</li>
     */
    private Long checkerId;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
