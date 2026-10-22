package com.paytech.platform.support.workflow.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.workflow.core.enums.VerificationResult;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_workflow_verification 映射对象：验证记录；只在 workflow 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
public class WorkflowVerification {

    /** 验证记录的 Snowflake 主键。 */
    private Long verificationId;

    /** 被验证的动作运行 ID。 */
    private Long actionRunId;

    /** 验证所属的流程实例 ID。 */
    private Long instanceId;

    /** 本次验证的结论。允许值：PASSED、FAILED。 */
    private VerificationResult result;

    /** 本次验证依据的证据，如文件地址列表、扫描状态或对象类型、ID、版本与状态；没有证据时为空。 */
    private String evidenceJson;

    /** 验证未通过的中文原因；通过时为空。 */
    private String failureReason;

    /** 提交或重新验证的 Operations 账号 ID。 */
    private Long verifiedBy;

    /** 本次验证完成的 UTC 微秒时间。 */
    private Instant verifiedTime;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;
}
