package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/** Decision Point 内不可原地改写的规则集版本。 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@Accessors(fluent = true)
public class RuleSetVersion {

    /** 规则集版本的稳定业务标识。 */
    private long ruleSetVersionId;

    /** 所属决策事件点的稳定业务标识。 */
    private long decisionPointId;

    /** Risk 受控目录中的规则集编码。 */
    @NonNull
    private String ruleSetCode;

    /** 规则集名称。 */
    @NonNull
    private String ruleSetName;

    /** 规则集版本号。 */
    private int versionNo;

    /** 规则集版本状态。 */
    @NonNull
    private RuleSetVersionStatus status;

    /** 规则集内容的 SHA-256 小写十六进制哈希。 */
    @NonNull
    private String contentHash;

    /** 创建者的 Actor Correlation。 */
    private long createdBy;

    /** 创建时冻结的非敏感变更原因。 */
    @NonNull
    private String changeReason;

    /** 冻结操作者；DRAFT 时为空。 */
    private Long frozenBy;

    /** 冻结 UTC 微秒时间；DRAFT 时为空。 */
    private Instant frozenTime;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private Instant createTime;

    /** 可变版本头最后一次持久化更新的 UTC 微秒时间。 */
    @NonNull
    private Instant updateTime;

    /** 内容格式：LEGACY 为原七类 Hash 合同，CONFIGURATION 为含附加动作的配置合同。 */
    @NonNull
    private String contentFormat;

    /** 该规则版本冻结的输入合同编码；取值来源：Risk 工程输入目录。 */
    @NonNull
    private String inputContractCode;

    /** 该输入合同的准确版本，不由当前最新目录推断。 */
    private int inputContractVersion;

    /** 仅在 Repository 已确认退役 CAS 成功后同步原对象。 */
    public void markRetired(Instant time) {
        status = RuleSetVersionStatus.RETIRED;
        updateTime = time;
    }

    /** 仅在 Repository 已确认冻结 CAS 成功后同步原对象。 */
    public void markFrozen(long actorId, Instant time) {
        status = RuleSetVersionStatus.FROZEN;
        frozenBy = actorId;
        frozenTime = time;
        updateTime = time;
    }

    /** 核验锁后的当前版本头与其不可变命名空间。 */
    public void verifyCurrentHead(long expectedPointId, String expectedRuleSetCode) {
        if (ruleSetVersionId <= 0 || decisionPointId != expectedPointId
            || !expectedRuleSetCode.equals(ruleSetCode) || versionNo <= 0
            || contentHash == null || !contentHash.matches("[0-9a-f]{64}")
            || status == RuleSetVersionStatus.RETIRED) {
            throw new com.paytech.foundation.common.error.ErrorException("锁后的最新 Rule Set Version 头损坏");
        }
        verifyStatusEvidence();
    }

    /** 状态与永久冻结证据必须一致，退休草稿不得附带冻结证据。 */
    public void verifyStatusEvidence() {
        if (status == null || createTime == null || updateTime == null
            || status == RuleSetVersionStatus.FROZEN && (frozenBy == null || frozenTime == null)
            || status != RuleSetVersionStatus.FROZEN && (frozenBy != null || frozenTime != null)) {
            throw new com.paytech.foundation.common.error.ErrorException("Rule Set Version 状态证据矩阵损坏");
        }
    }
}
