package com.paytech.platform.support.api.workflow.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WorkflowCallbackRaised v1 的 Payload：工作流节点或实例状态变化命中业务回调规则（技术方案 4.2）。
 * <li>发布到 platform-support.events；订阅方按 callbackCode 过滤，以信封 eventId 幂等。</li>
 * <li>事件只是通知，不能作为业务执行开关；订阅方执行前须自行校验业务事实（D13）。不携带表单值或文件。</li>
 * <li>aggregateType 为 WORKFLOW_INSTANCE，aggregateId 为实例 ID，aggregateVersion 为变化后的实例版本（可能跳号）。</li>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowCallbackRaisedPayload {

    /** 事件类型。 */
    public static final String EVENT_TYPE = "WorkflowCallbackRaised";

    /** 聚合类型。 */
    public static final String AGGREGATE_TYPE = "WORKFLOW_INSTANCE";

    /** 当前 Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 通知回调记录 ID（十进制字符串），同一记录的重试发布相同 eventId。 */
    private String callbackId;

    /** 回调编码。 */
    private String callbackCode;

    /** 触发事件。 */
    private String trigger;

    /** 流程编码。 */
    private String definitionCode;

    /** 实例绑定的版本号。 */
    private Integer versionNo;

    /** 实例 ID（十进制字符串）。 */
    private String instanceId;

    /** 业务关联键；可空。 */
    private String businessKey;

    /** 节点 key；实例级为空。 */
    private String nodeKey;

    /** 节点轮次；实例级为空。 */
    private Integer roundNo;

    /** 变化前状态；可空。 */
    private String fromStatus;

    /** 变化后状态；可空。 */
    private String toStatus;

    /** 触发账号（十进制字符串）；系统推进为空。 */
    private String actorId;

    /** 意见、说明或原因；可空。 */
    private String comment;
}
