package com.paytech.risk.api.decision;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Risk TEST Candidate 管理 HTTP 模型。 */
public final class DecisionReleaseManagementModels {

    private DecisionReleaseManagementModels() {
    }

    /** TEST Candidate 的受控协议结构。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static final class FrozenCompositionResponseBody {

        /** 准确决策点 ID，以十进制字符串传输。 */
        private String decisionPointId;

        /** Risk 决策点编码，本期为 CHECKOUT_PREPARE。 */
        private String pointCode;

        /** 旧发布组成中的准确 Point Version ID。 */
        private String pointVersionId;

        /** 旧 Point Version 业务版本号。 */
        private Integer pointVersionNo;

        /** 旧 Point Version 的内容 SHA-256。 */
        private String pointVersionContentHash;

        /** 准确冻结规则版本 ID，以正 BIGINT 十进制字符串传输。 */
        private String ruleSetVersionId;

        /** 规则集不可变业务编码。 */
        private String ruleSetCode;

        /** 准确规则版本的业务版本号。 */
        private Integer ruleSetVersionNo;

        /** 准确规则版本的内容 SHA-256。 */
        private String ruleSetContentHash;

        /** 供普通 JSON 属性绑定使用。 */
        public FrozenCompositionResponseBody() { }

        /** 根据完整协议字段构造。 */
        public FrozenCompositionResponseBody(String decisionPointId, String pointCode, String pointVersionId, Integer pointVersionNo, String pointVersionContentHash, String ruleSetVersionId, String ruleSetCode, Integer ruleSetVersionNo, String ruleSetContentHash) {
            this.decisionPointId = decisionPointId;
            this.pointCode = pointCode;
            this.pointVersionId = pointVersionId;
            this.pointVersionNo = pointVersionNo;
            this.pointVersionContentHash = pointVersionContentHash;
            this.ruleSetVersionId = ruleSetVersionId;
            this.ruleSetCode = ruleSetCode;
            this.ruleSetVersionNo = ruleSetVersionNo;
            this.ruleSetContentHash = ruleSetContentHash;
        }

        /** 返回准确决策点 ID，以十进制字符串传输。 */
        public String getDecisionPointId() { return decisionPointId; }

        /** 设置准确决策点 ID，以十进制字符串传输。 */
        public void setDecisionPointId(String decisionPointId) { this.decisionPointId = decisionPointId; }

        /** 返回Risk 决策点编码，本期为 CHECKOUT_PREPARE。 */
        public String getPointCode() { return pointCode; }

        /** 设置Risk 决策点编码，本期为 CHECKOUT_PREPARE。 */
        public void setPointCode(String pointCode) { this.pointCode = pointCode; }

        /** 返回旧发布组成中的准确 Point Version ID。 */
        public String getPointVersionId() { return pointVersionId; }

        /** 设置旧发布组成中的准确 Point Version ID。 */
        public void setPointVersionId(String pointVersionId) { this.pointVersionId = pointVersionId; }

        /** 返回旧 Point Version 业务版本号。 */
        public Integer getPointVersionNo() { return pointVersionNo; }

        /** 设置旧 Point Version 业务版本号。 */
        public void setPointVersionNo(Integer pointVersionNo) { this.pointVersionNo = pointVersionNo; }

        /** 返回旧 Point Version 的内容 SHA-256。 */
        public String getPointVersionContentHash() { return pointVersionContentHash; }

        /** 设置旧 Point Version 的内容 SHA-256。 */
        public void setPointVersionContentHash(String pointVersionContentHash) { this.pointVersionContentHash = pointVersionContentHash; }

        /** 返回准确冻结规则版本 ID，以正 BIGINT 十进制字符串传输。 */
        public String getRuleSetVersionId() { return ruleSetVersionId; }

        /** 设置准确冻结规则版本 ID，以正 BIGINT 十进制字符串传输。 */
        public void setRuleSetVersionId(String ruleSetVersionId) { this.ruleSetVersionId = ruleSetVersionId; }

        /** 返回规则集不可变业务编码。 */
        public String getRuleSetCode() { return ruleSetCode; }

        /** 设置规则集不可变业务编码。 */
        public void setRuleSetCode(String ruleSetCode) { this.ruleSetCode = ruleSetCode; }

        /** 返回准确规则版本的业务版本号。 */
        public Integer getRuleSetVersionNo() { return ruleSetVersionNo; }

        /** 设置准确规则版本的业务版本号。 */
        public void setRuleSetVersionNo(Integer ruleSetVersionNo) { this.ruleSetVersionNo = ruleSetVersionNo; }

        /** 返回准确规则版本的内容 SHA-256。 */
        public String getRuleSetContentHash() { return ruleSetContentHash; }

        /** 设置准确规则版本的内容 SHA-256。 */
        public void setRuleSetContentHash(String ruleSetContentHash) { this.ruleSetContentHash = ruleSetContentHash; }
    }

    /** TEST Candidate 的受控协议结构。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static final class ReleaseStateChangeResponseBody {

        /** 不可变审计事件 ID。 */
        private String auditEventId;

        /** 事件时刻，UTC 六位微秒。 */
        private String eventTime;

        /** 原状态；初始创建为空。 */
        private String fromStatus;

        /** 状态变更后的状态。 */
        private String toStatus;

        /** 受控状态变更原因编码。 */
        private String reasonCode;

        /** 触发类型，本期为 OPERATIONS_COMMAND。 */
        private String triggerType;

        /** 不可变触发身份，64 位小写十六进制；不返回 HTTP 请求 Key。 */
        private String triggerId;

        /** 操作人的 Account ID。 */
        private String actorId;

        /** 该状态变更对应的聚合版本。 */
        private Integer aggregateVersion;

        /** 供普通 JSON 属性绑定使用。 */
        public ReleaseStateChangeResponseBody() { }

        /** 根据完整协议字段构造。 */
        public ReleaseStateChangeResponseBody(String auditEventId, String eventTime, String fromStatus, String toStatus, String reasonCode, String triggerType, String triggerId, String actorId, Integer aggregateVersion) {
            this.auditEventId = auditEventId;
            this.eventTime = eventTime;
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.reasonCode = reasonCode;
            this.triggerType = triggerType;
            this.triggerId = triggerId;
            this.actorId = actorId;
            this.aggregateVersion = aggregateVersion;
        }

        /** 返回不可变审计事件 ID。 */
        public String getAuditEventId() { return auditEventId; }

        /** 设置不可变审计事件 ID。 */
        public void setAuditEventId(String auditEventId) { this.auditEventId = auditEventId; }

        /** 返回事件时刻，UTC 六位微秒。 */
        public String getEventTime() { return eventTime; }

        /** 设置事件时刻，UTC 六位微秒。 */
        public void setEventTime(String eventTime) { this.eventTime = eventTime; }

        /** 返回原状态；初始创建为空。 */
        public String getFromStatus() { return fromStatus; }

        /** 设置原状态；初始创建为空。 */
        public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

        /** 返回状态变更后的状态。 */
        public String getToStatus() { return toStatus; }

        /** 设置状态变更后的状态。 */
        public void setToStatus(String toStatus) { this.toStatus = toStatus; }

        /** 返回受控状态变更原因编码。 */
        public String getReasonCode() { return reasonCode; }

        /** 设置受控状态变更原因编码。 */
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

        /** 返回触发类型，本期为 OPERATIONS_COMMAND。 */
        public String getTriggerType() { return triggerType; }

        /** 设置触发类型，本期为 OPERATIONS_COMMAND。 */
        public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

        /** 返回不可变触发身份，64 位小写十六进制；不返回 HTTP 请求 Key。 */
        public String getTriggerId() { return triggerId; }

        /** 设置不可变触发身份，64 位小写十六进制；不返回 HTTP 请求 Key。 */
        public void setTriggerId(String triggerId) { this.triggerId = triggerId; }

        /** 返回操作人的 Account ID。 */
        public String getActorId() { return actorId; }

        /** 设置操作人的 Account ID。 */
        public void setActorId(String actorId) { this.actorId = actorId; }

        /** 返回该状态变更对应的聚合版本。 */
        public Integer getAggregateVersion() { return aggregateVersion; }

        /** 设置该状态变更对应的聚合版本。 */
        public void setAggregateVersion(Integer aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    }

    /** TEST Candidate 的受控协议结构。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static final class ReleaseStaticCheckResponseBody {

        /** 不可变审计事件 ID。 */
        private String auditEventId;

        /** 事件时刻，UTC 六位微秒。 */
        private String eventTime;

        /** 八项静态检查中的准确编码。 */
        private String checkCode;

        /** 检查状态，PASSED 或 FAILED。 */
        private String checkStatus;

        /** 检查级别，通过为 INFO，失败为 ERROR。 */
        private String severity;

        /** 失败的受控错误编码，通过时为空。 */
        private String errorCode;

        /** 脱敏失败摘要，通过时为空，不包含规则字面值。 */
        private String errorSummary;

        /** 供普通 JSON 属性绑定使用。 */
        public ReleaseStaticCheckResponseBody() { }

        /** 根据完整协议字段构造。 */
        public ReleaseStaticCheckResponseBody(String auditEventId, String eventTime, String checkCode, String checkStatus, String severity, String errorCode, String errorSummary) {
            this.auditEventId = auditEventId;
            this.eventTime = eventTime;
            this.checkCode = checkCode;
            this.checkStatus = checkStatus;
            this.severity = severity;
            this.errorCode = errorCode;
            this.errorSummary = errorSummary;
        }

        /** 返回不可变审计事件 ID。 */
        public String getAuditEventId() { return auditEventId; }

        /** 设置不可变审计事件 ID。 */
        public void setAuditEventId(String auditEventId) { this.auditEventId = auditEventId; }

        /** 返回事件时刻，UTC 六位微秒。 */
        public String getEventTime() { return eventTime; }

        /** 设置事件时刻，UTC 六位微秒。 */
        public void setEventTime(String eventTime) { this.eventTime = eventTime; }

        /** 返回八项静态检查中的准确编码。 */
        public String getCheckCode() { return checkCode; }

        /** 设置八项静态检查中的准确编码。 */
        public void setCheckCode(String checkCode) { this.checkCode = checkCode; }

        /** 返回检查状态，PASSED 或 FAILED。 */
        public String getCheckStatus() { return checkStatus; }

        /** 设置检查状态，PASSED 或 FAILED。 */
        public void setCheckStatus(String checkStatus) { this.checkStatus = checkStatus; }

        /** 返回检查级别，通过为 INFO，失败为 ERROR。 */
        public String getSeverity() { return severity; }

        /** 设置检查级别，通过为 INFO，失败为 ERROR。 */
        public void setSeverity(String severity) { this.severity = severity; }

        /** 返回失败的受控错误编码，通过时为空。 */
        public String getErrorCode() { return errorCode; }

        /** 设置失败的受控错误编码，通过时为空。 */
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

        /** 返回脱敏失败摘要，通过时为空，不包含规则字面值。 */
        public String getErrorSummary() { return errorSummary; }

        /** 设置脱敏失败摘要，通过时为空，不包含规则字面值。 */
        public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    }

    /** TEST Candidate 的受控协议结构。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static final class InitialStateChangeResponseBody {

        /** 不可变审计事件 ID。 */
        private String auditEventId;

        /** 事件时刻，UTC 六位微秒。 */
        private String eventTime;

        /** 原状态；初始创建为空。 */
        private String fromStatus;

        /** 状态变更后的状态。 */
        private String toStatus;

        /** 受控状态变更原因编码。 */
        private String reasonCode;

        /** 触发类型，本期为 OPERATIONS_COMMAND。 */
        private String triggerType;

        /** 操作人的 Account ID。 */
        private String actorId;

        /** 该状态变更对应的聚合版本。 */
        private Integer aggregateVersion;

        /** 供普通 JSON 属性绑定使用。 */
        public InitialStateChangeResponseBody() { }

        /** 根据完整协议字段构造。 */
        public InitialStateChangeResponseBody(String auditEventId, String eventTime, String fromStatus, String toStatus, String reasonCode, String triggerType, String actorId, Integer aggregateVersion) {
            this.auditEventId = auditEventId;
            this.eventTime = eventTime;
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.reasonCode = reasonCode;
            this.triggerType = triggerType;
            this.actorId = actorId;
            this.aggregateVersion = aggregateVersion;
        }

        /** 返回不可变审计事件 ID。 */
        public String getAuditEventId() { return auditEventId; }

        /** 设置不可变审计事件 ID。 */
        public void setAuditEventId(String auditEventId) { this.auditEventId = auditEventId; }

        /** 返回事件时刻，UTC 六位微秒。 */
        public String getEventTime() { return eventTime; }

        /** 设置事件时刻，UTC 六位微秒。 */
        public void setEventTime(String eventTime) { this.eventTime = eventTime; }

        /** 返回原状态；初始创建为空。 */
        public String getFromStatus() { return fromStatus; }

        /** 设置原状态；初始创建为空。 */
        public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

        /** 返回状态变更后的状态。 */
        public String getToStatus() { return toStatus; }

        /** 设置状态变更后的状态。 */
        public void setToStatus(String toStatus) { this.toStatus = toStatus; }

        /** 返回受控状态变更原因编码。 */
        public String getReasonCode() { return reasonCode; }

        /** 设置受控状态变更原因编码。 */
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

        /** 返回触发类型，本期为 OPERATIONS_COMMAND。 */
        public String getTriggerType() { return triggerType; }

        /** 设置触发类型，本期为 OPERATIONS_COMMAND。 */
        public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

        /** 返回操作人的 Account ID。 */
        public String getActorId() { return actorId; }

        /** 设置操作人的 Account ID。 */
        public void setActorId(String actorId) { this.actorId = actorId; }

        /** 返回该状态变更对应的聚合版本。 */
        public Integer getAggregateVersion() { return aggregateVersion; }

        /** 设置该状态变更对应的聚合版本。 */
        public void setAggregateVersion(Integer aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    }

    /** TEST Candidate 的受控协议结构。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static final class ValidationCheckResponseBody {

        /** 八项静态检查中的准确编码。 */
        private String checkCode;

        /** 检查状态，PASSED 或 FAILED。 */
        private String checkStatus;

        /** 检查级别，通过为 INFO，失败为 ERROR。 */
        private String severity;

        /** 失败的受控错误编码，通过时为空。 */
        private String errorCode;

        /** 脱敏失败摘要，通过时为空，不包含规则字面值。 */
        private String errorSummary;

        /** 供普通 JSON 属性绑定使用。 */
        public ValidationCheckResponseBody() { }

        /** 根据完整协议字段构造。 */
        public ValidationCheckResponseBody(String checkCode, String checkStatus, String severity, String errorCode, String errorSummary) {
            this.checkCode = checkCode;
            this.checkStatus = checkStatus;
            this.severity = severity;
            this.errorCode = errorCode;
            this.errorSummary = errorSummary;
        }

        /** 返回八项静态检查中的准确编码。 */
        public String getCheckCode() { return checkCode; }

        /** 设置八项静态检查中的准确编码。 */
        public void setCheckCode(String checkCode) { this.checkCode = checkCode; }

        /** 返回检查状态，PASSED 或 FAILED。 */
        public String getCheckStatus() { return checkStatus; }

        /** 设置检查状态，PASSED 或 FAILED。 */
        public void setCheckStatus(String checkStatus) { this.checkStatus = checkStatus; }

        /** 返回检查级别，通过为 INFO，失败为 ERROR。 */
        public String getSeverity() { return severity; }

        /** 设置检查级别，通过为 INFO，失败为 ERROR。 */
        public void setSeverity(String severity) { this.severity = severity; }

        /** 返回失败的受控错误编码，通过时为空。 */
        public String getErrorCode() { return errorCode; }

        /** 设置失败的受控错误编码，通过时为空。 */
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

        /** 返回脱敏失败摘要，通过时为空，不包含规则字面值。 */
        public String getErrorSummary() { return errorSummary; }

        /** 设置脱敏失败摘要，通过时为空，不包含规则字面值。 */
        public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    }

}
