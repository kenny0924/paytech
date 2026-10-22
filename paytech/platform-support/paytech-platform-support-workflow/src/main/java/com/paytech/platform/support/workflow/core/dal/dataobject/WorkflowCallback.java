package com.paytech.platform.support.workflow.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.workflow.core.enums.CallbackStatus;
import com.paytech.platform.support.workflow.core.enums.NotificationRuleType;
import com.paytech.platform.support.workflow.core.enums.NotificationTrigger;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_workflow_callback 映射对象：通知与回调；只在 workflow 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
public class WorkflowCallback {

    /** 通知或回调记录的 Snowflake 主键，也是补偿任务与回调事件的业务键。 */
    private Long callbackId;

    /** 触发通知或回调的流程实例 ID。 */
    private Long instanceId;

    /** 节点级触发的节点运行 ID；实例级触发为空。 */
    private Long nodeRunId;

    /** 触发规则的状态变化；STARTED、COMPLETED、TERMINATED 为实例级，其余为节点级。允许值：ACTIVATED、APPROVAL_OPENED、PASSED、RETURNED_FOR_REVISION、REJECTED、VERIFICATION_FAILED、TRANSFERRED、STARTED、COMPLETED、TERMINATED。 */
    private NotificationTrigger triggerCode;

    /** NOTIFY 通知人员（邮件）；CALLBACK 向 topic_platform_workflow_event 发布业务事件。允许值：NOTIFY、CALLBACK。 */
    private NotificationRuleType callbackType;

    /** NOTIFY 为解析后的收件人账号 ID 列表与渠道；CALLBACK 为回调编码；登记后不可变。 */
    private String target;

    /** PENDING 待发送或重试中；SENT 已送达；FAILED 超过重试上限，可人工重试。允许值：PENDING、SENT、FAILED。 */
    private CallbackStatus status;

    /** 已执行的发送次数，初始为 0。 */
    private Integer attemptCount;

    /** 最近一次失败的脱敏错误摘要；未失败时为空。 */
    private String lastError;

    /** 发送成功的 UTC 微秒时间；未送达时为空。 */
    private Instant sentTime;

    /** 并发更新版本，初始为 1，每次条件更新成功加一。 */
    private Integer version;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;

    /** 该可变记录最后一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
