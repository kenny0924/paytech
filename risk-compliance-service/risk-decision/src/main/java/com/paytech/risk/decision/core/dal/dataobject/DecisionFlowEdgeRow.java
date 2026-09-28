package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** 保存版本内节点连线、显式出口顺序及受控条件树；一条连线一个条件，不保存求值结果。 */
@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public class DecisionFlowEdgeRow {
    /**
     * Risk 服务侧 Snowflake 生成的版本内连线行身份。
     */
    private final long edgeId;

    /**
     * 所属不可变流程版本。
     * <li>来源和目标节点必须同属此版本。</li>
     */
    private final long flowVersionId;

    /**
     * 版本内大小写敏感的稳定连线键。
     * <li>不以创建时间或坐标决定顺序。</li>
     */
    private final String edgeKey;

    /**
     * 本库同一流程版本的来源节点。
     * <li>只允许 START 或 RULE_SET。</li>
     */
    private final long sourceNodeId;

    /**
     * 本库同一流程版本的目标节点。
     * <li>不能指向 START 或自身。</li>
     */
    private final long targetNodeId;

    /**
     * 出口业务含义。允许值：UNCONDITIONAL、CONDITIONAL、ELSE。
     */
    private final FlowEdgeType edgeType;

    /**
     * CONDITIONAL 从 1 连续排列且同源唯一。
     * <li>ELSE 与 UNCONDITIONAL 固定为 0，两者不能同源共存。</li>
     */
    private final int priorityNo;

    /**
     * 运营填写的连线名称快照。
     * <li>1 至 100 个 Unicode 码点。</li>
     */
    private final String edgeName;

    /**
     * 仅 CONDITIONAL 必填。
     * <li>受控 schemaVersion=1 JSON Object，规范化后最多 8192 UTF-8 字节，最多 32 个条件树节点、深度 8；其他类型为空。仅整树读取，不用 JSON 路径做数据库检索。</li>
     */
    private final String conditionJson;

    /**
     * 本行在 Risk 数据库中创建的 UTC 微秒时间。
     */
    private final Instant createTime;

    /**
     * 不可变配置明细。
     * <li>始终等于 create_time。</li>
     */
    private final Instant updateTime;

    /** 校验本行所属版本和不可变审计时间，不执行外部查询。 */
    public void verifyVersion(DecisionFlowVersionRow version) {
        if (edgeId <= 0 || flowVersionId != version.flowVersionId() || edgeType == null
            || createTime == null || !createTime.equals(version.createTime()) || !createTime.equals(updateTime)) {
            throw new ErrorException("流程明细版本身份或审计时间损坏");
        }
    }
}
