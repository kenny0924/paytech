package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowNodeType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** 保存每个配置版本的开始、规则集和结束节点；规则版本与原因直接可查，不保存运行结果。 */
@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public class DecisionFlowNodeRow {
    /**
     * Risk 服务侧 Snowflake 生成的版本内节点行身份。
     * <li>每次另存生成新行。</li>
     */
    private final long nodeId;

    /**
     * 所属不可变流程版本。
     * <li>所有节点及连线必须处于同一版本。</li>
     */
    private final long flowVersionId;

    /**
     * 版本内大小写敏感的节点键。
     * <li>同一编辑节点跨版本保留，删除再添加使用新键。</li>
     */
    private final String nodeKey;

    /**
     * 节点业务类型。允许值：START、RULE_SET、END。
     */
    private final FlowNodeType nodeType;

    /**
     * 保存时的节点名称快照。
     * <li>1 至 100 个 Unicode 码点。</li>
     */
    private final String nodeName;

    /**
     * 准确引用本库规则集版本。
     * <li>仅 RULE_SET 必填，其他类型为空；不追随最新版本。</li>
     */
    private final Long ruleSetVersionId;

    /**
     * 仅 END 必填。
     * <li>其他类型为空。允许值：ALLOW、REVIEW、DENY。</li>
     */
    private final DecisionValue decisionCode;

    /**
     * 仅 DENY 结束节点必填。
     * <li>其他情况为空。取值来源：本库拒绝原因目录；非固定枚举。</li>
     */
    private final String reasonCode;

    /**
     * 仅 DENY 结束节点必填。
     * <li>保存时的内部拒绝说明；目录修改不回写，其他情况为空。</li>
     */
    private final String reasonMessage;

    /**
     * 编辑画布中的整数像素坐标。
     * <li>范围负 100000 至正 100000；HTTP 使用十进制字符串。</li>
     */
    private final int positionX;

    /**
     * 编辑画布中的整数像素坐标。
     * <li>范围负 100000 至正 100000；HTTP 使用十进制字符串。</li>
     */
    private final int positionY;

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
        if (nodeId <= 0 || flowVersionId != version.flowVersionId() || nodeType == null
            || createTime == null || !createTime.equals(version.createTime()) || !createTime.equals(updateTime)) {
            throw new ErrorException("流程明细版本身份或审计时间损坏");
        }
    }
}
