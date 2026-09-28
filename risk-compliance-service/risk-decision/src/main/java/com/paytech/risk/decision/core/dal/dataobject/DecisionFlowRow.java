package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import java.util.List;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionFlowConfigurationException.Reason;
import com.paytech.risk.decision.domainservice.service.DecisionFlowConfigurationException;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** 保存决策流独立身份、当前配置入口及整体删除状态；不表达运行发布状态。 */
@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public class DecisionFlowRow {
    /**
     * Risk 服务侧 Snowflake 生成的流程管理身份。
     * <li>创建后不变。</li>
     */
    private final long flowId;

    /**
     * 本库决策点身份。
     * <li>与流程编码共同确定不可改变的业务身份。</li>
     */
    private final long decisionPointId;

    /**
     * 决策点内永久唯一的大写流程编码。
     * <li>删除后不回收。取值来源：Risk 流程配置管理；非固定枚举。</li>
     */
    private final String flowCode;

    /**
     * 本流程当前最高业务版本。
     * <li>与新版本在同一事务切换，删除后保留。</li>
     */
    private long currentFlowVersionId;

    /**
     * 与运行发布无关的整体管理状态。允许值：CONFIGURABLE、DELETED。
     */
    private FlowStatus status;

    /**
     * 从 1 开始。
     * <li>每次保存新配置或首次删除加 1。</li>
     */
    private long version;

    /**
     * 来源 Platform Account 的永久账号标识。
     * <li>只保存跨系统身份引用；CONFIGURABLE 时为空，DELETED 时必填且不改写。</li>
     */
    private Long deletedBy;

    /**
     * 整体删除的 UTC 微秒时间。
     * <li>CONFIGURABLE 时为空，DELETED 时必填且不改写。</li>
     */
    private Instant deletedTime;

    /**
     * 本行在 Risk 数据库中创建的 UTC 微秒时间。
     */
    private final Instant createTime;

    /**
     * 当前版本指针或删除状态最后改变的 UTC 微秒时间。
     * <li>查询及失败的条件写入不改变。</li>
     */
    private Instant updateTime;

    /** 校验任一历史版本的根归属及输入合同，不要求它是当前配置。 */
    public void verifyVersion(DecisionFlowVersionRow value) {
        if (value == null || flowId <= 0 || decisionPointId <= 0 || version <= 0
            || flowCode == null || status == null || createTime == null || updateTime == null
            || value.flowVersionId() <= 0 || value.flowId() != flowId || value.versionNo() < 1
            || value.createdBy() <= 0 || value.createTime() == null || !value.createTime().equals(value.updateTime())
            || !"ORDER_PREPARATION".equals(value.inputContractCode()) || value.inputContractVersion() != 4) {
            throw new ErrorException("流程持久化配置不完整或身份不一致");
        }
    }

    /** 当前指针、最高业务版本与整体删除审计必须一致。 */
    public void verifyHead(DecisionFlowVersionRow head) {
        verifyVersion(head);
        if (currentFlowVersionId != head.flowVersionId() || version != (long) head.versionNo()
            + (status == FlowStatus.DELETED ? 1 : 0)
            || (status == FlowStatus.DELETED ? deletedBy == null || deletedBy <= 0 || deletedTime == null
                : deletedBy != null || deletedTime != null)) {
            throw new ErrorException("流程持久化配置不完整或身份不一致");
        }
    }

    /** 编辑只允许从当前准确版本继续保存，已删除流程不再产生新版本。 */
    public void verifySaveBase(long baseVersionId, long expectedVersion) {
        verifyConfigurable();
        if (currentFlowVersionId != baseVersionId || version != expectedVersion) {
            throw failure(Reason.VERSION_CONFLICT,
                "流程当前版本已变化，请重新载入后编辑");
        }
    }

    /** 删除只确认调用方读取过的根修订号；重复删除不创建第二份删除事实。 */
    public void verifyDeleteVersion(long expectedVersion) {
        verifyConfigurable();
        if (version != expectedVersion) {
            throw failure(Reason.VERSION_CONFLICT,
                "流程已变化，请刷新后重新确认删除");
        }
    }

    /** 当前入口隐藏已删除流程，准确历史入口不使用此限制。 */
    public void verifyCurrentReadable() {
        if (status == FlowStatus.DELETED) {
            throw new DecisionConfigurationNotFoundException();
        }
    }

    /** 路径中的准确版本必须属于当前流程，跨流程查询按不存在处理。 */
    public void verifyOwnedVersion(DecisionFlowVersionRow selected) {
        if (selected.flowId() != flowId) {
            throw new DecisionConfigurationNotFoundException();
        }
        verifyVersion(selected);
    }

    /** 版本浏览上界不得超过当前真实头版本；整体删除仍允许浏览历史。 */
    public void verifyHistoryLimit(DecisionFlowVersionRow head, int maxVersionNo) {
        verifyHead(head);
        if (maxVersionNo < 1 || maxVersionNo > head.versionNo()) {
            throw new InvalidParameterException("maxVersionNo 超出流程历史范围");
        }
    }

    /** SQL 成功后同步当前实例的版本指针与修订号。 */
    public void advance(DecisionFlowVersionRow next) {
        currentFlowVersionId = next.flowVersionId();
        version++;
        updateTime = next.createTime();
    }

    /** SQL 成功后同步当前实例的整体删除事实，保留全部配置历史。 */
    public void delete(long actorId, Instant time) {
        status = FlowStatus.DELETED;
        version++;
        deletedBy = actorId;
        deletedTime = time;
        updateTime = time;
    }

    /** 已删除的永久身份不允许再次编辑或删除。 */
    private void verifyConfigurable() {
        if (status == FlowStatus.DELETED) {
            throw failure(Reason.DELETED,
                "流程已删除");
        }
    }

    /** 构造流程业务冲突，不携带配置正文。 */
    private static DecisionFlowConfigurationException failure(
        Reason reason, String message) {
        return new DecisionFlowConfigurationException(reason, message, List.of());
    }
}
