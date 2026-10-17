package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** 每次完整保存生成的不可变配置版本头；历史只读，不是运行发布版本。 */
@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public class DecisionFlowVersionRow {
    /**
     * Risk 服务侧 Snowflake 生成的不可变配置版本身份。
     */
    private final long flowVersionId;

    /**
     * 所属本库决策流管理身份。
     * <li>创建后不变。</li>
     */
    private final long flowId;

    /**
     * 同一流程内从 1 开始连续递增。
     * <li>只在成功保存新配置时增加。</li>
     */
    private final int versionNo;

    /**
     * 本次保存的流程名称快照。
     * <li>1 至 100 个 Unicode 码点；以后改名不回写。</li>
     */
    private final String flowName;

    /**
     * 保存时选定的 Risk 决策点输入合同。取值来源：Risk 输入合同目录。
     * <li>非固定枚举。</li>
     */
    private final String inputContractCode;

    /**
     * 保存时输入合同的准确正整数版本。
     * <li>规则集引用必须与其一致。</li>
     */
    private final int inputContractVersion;

    /**
     * 来源 Platform Account 的永久账号标识。
     * <li>只保存可信操作者身份，不复制账号资料。</li>
     */
    private final long createdBy;

    /**
     * 本次完整保存的人工说明。
     * <li>1 至 500 个 Unicode 码点；不包含个人原值或凭据。</li>
     */
    private final String changeReason;

    /**
     * 本行在 Risk 数据库中创建的 UTC 微秒时间。
     */
    private final Instant createTime;

    /**
     * 不可变配置明细。
     * <li>始终等于 create_time。</li>
     */
    private final Instant updateTime;

}
