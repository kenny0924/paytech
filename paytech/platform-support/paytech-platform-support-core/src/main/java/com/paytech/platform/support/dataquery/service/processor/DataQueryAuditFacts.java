package com.paytech.platform.support.dataquery.service.processor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.dataquery.domain.DataSourceChange;
import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeChange;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeReorder;
import com.paytech.platform.support.dataquery.domain.QueryTemplateChange;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDraft;
import com.paytech.platform.support.dataquery.domain.QueryTemplateJson;

/**
 * 数据源、模板与键类型写入的审计事实：只记编码、名称、状态、版本与内容摘要；不记密码、Vault Token 或正文原文。
 * <li>连接参数以规范化 SHA-256 摘要记录（DEFINITION_DIGEST），密码只记 SET / REPLACED（CREDENTIAL）。</li>
 */
final class DataQueryAuditFacts {

    /** 数据源审计资源类型。 */
    static final String DATA_SOURCE = "DATA_SOURCE";

    /** 模板审计资源类型。 */
    static final String QUERY_TEMPLATE = "QUERY_TEMPLATE";

    /** 键类型审计资源类型。 */
    static final String QUERY_KEY_TYPE = "QUERY_KEY_TYPE";

    /** 键类型整体重排的审计资源。 */
    static final String QUERY_KEY_TYPE_ORDER = "QUERY_KEY_TYPE_ORDER";

    private DataQueryAuditFacts() {
    }

    /**
     * 数据源写入。
     *
     * @param actor 操作人
     * @param tenantId 操作人所属租户
     * @param change 前后事实
     * @param reason 操作说明
     * @param now 写入时间
     * @return 审计事实
     */
    static ConfigurationChanged dataSource(String actor, long tenantId, DataSourceChange change, String reason,
        Instant now) {
        DataSourceDetail before = change.getBefore();
        DataSourceDetail after = change.getAfter();
        List<ConfigurationFieldChange> fields = List.of(
            field(ConfigurationField.CODE, before == null ? null : before.getDataSourceCode(), after.getDataSourceCode()),
            field(ConfigurationField.DISPLAY_NAME, before == null ? null : before.getDataSourceName(),
                after.getDataSourceName()),
            field(ConfigurationField.STATUS, before == null ? null : before.getStatus().getCode(),
                after.getStatus().getCode()),
            field(ConfigurationField.DEFINITION_DIGEST, before == null ? null : connectionDigest(before),
                connectionDigest(after)),
            field(ConfigurationField.CREDENTIAL, before == null ? null : "SET", before == null
                || Objects.equals(before.getPasswordToken(), after.getPasswordToken()) ? "SET" : "REPLACED"));
        return new ConfigurationChanged(new Identity(actor, tenantId),
            new Target(DATA_SOURCE, Long.toString(after.getDataSourceId())), before == null ? 0 : before.getVersion(),
            after.getVersion(), fields, reason, now);
    }

    /**
     * 模板写入。
     *
     * @param actor 操作人
     * @param tenantId 操作人所属租户
     * @param change 前后事实
     * @param reason 操作说明
     * @param now 写入时间
     * @return 审计事实
     */
    static ConfigurationChanged template(String actor, long tenantId, QueryTemplateChange change, String reason,
        Instant now) {
        QueryTemplateDetail before = change.getBefore();
        QueryTemplateDetail after = change.getAfter();
        QueryTemplateDraft beforeContent = before == null ? null : before.getCurrentVersion().getContent();
        QueryTemplateDraft afterContent = after.getCurrentVersion().getContent();
        List<ConfigurationFieldChange> fields = List.of(
            field(ConfigurationField.CODE, before == null ? null : before.getTemplateCode(), after.getTemplateCode()),
            field(ConfigurationField.DISPLAY_NAME, before == null ? null : before.getTemplateName(),
                after.getTemplateName()),
            field(ConfigurationField.STATUS, before == null ? null : before.getStatus().getCode(),
                after.getStatus().getCode()),
            field(ConfigurationField.TARGET, beforeContent == null ? null : Long.toString(beforeContent.getDataSourceId()),
                Long.toString(afterContent.getDataSourceId())),
            field(ConfigurationField.SOURCE_DIGEST, beforeContent == null ? null : sha256(beforeContent.getBody()),
                sha256(afterContent.getBody())),
            field(ConfigurationField.PARAMETERS_DIGEST, beforeContent == null ? null : parametersDigest(beforeContent),
                parametersDigest(afterContent)),
            field(ConfigurationField.DEFINITION_DIGEST, beforeContent == null ? null : definitionDigest(beforeContent),
                definitionDigest(afterContent)));
        return new ConfigurationChanged(new Identity(actor, tenantId),
            new Target(QUERY_TEMPLATE, Long.toString(after.getTemplateId())), before == null ? 0 : before.getVersion(),
            after.getVersion(), fields, reason, now);
    }

    /**
     * 键类型写入。
     *
     * @param actor 操作人
     * @param tenantId 操作人所属租户
     * @param change 前后事实
     * @param reason 操作说明
     * @param now 写入时间
     * @return 审计事实
     */
    static ConfigurationChanged keyType(String actor, long tenantId, QueryKeyTypeChange change, String reason,
        Instant now) {
        QueryKeyTypeDetail before = change.getBefore();
        QueryKeyTypeDetail after = change.getAfter();
        List<ConfigurationFieldChange> fields = List.of(
            field(ConfigurationField.CODE, before == null ? null : before.getKeyTypeCode(), after.getKeyTypeCode()),
            field(ConfigurationField.DISPLAY_NAME, before == null ? null : before.getKeyTypeName(),
                after.getKeyTypeName()),
            field(ConfigurationField.STATUS, before == null ? null : before.getStatus().getCode(),
                after.getStatus().getCode()),
            field(ConfigurationField.SORT_ORDER, before == null ? null : Integer.toString(before.getSortNo()),
                Integer.toString(after.getSortNo())),
            field(ConfigurationField.DEFINITION_DIGEST, before == null ? null : keyTypeDigest(before),
                keyTypeDigest(after)));
        return new ConfigurationChanged(new Identity(actor, tenantId),
            new Target(QUERY_KEY_TYPE, Long.toString(after.getKeyTypeId())), before == null ? 0 : before.getVersion(),
            after.getVersion(), fields, reason, now);
    }

    /**
     * 键类型整体重排：只记前后顺序的摘要与项数。
     *
     * @param actor 操作人
     * @param tenantId 操作人所属租户
     * @param reorder 重排前后
     * @param now 写入时间
     * @return 审计事实
     */
    static ConfigurationChanged keyTypeOrder(String actor, long tenantId, QueryKeyTypeReorder reorder, Instant now) {
        List<ConfigurationFieldChange> fields = List.of(field(ConfigurationField.SORT_ORDER,
            orderDigest(reorder.getBefore()), orderDigest(reorder.getAfter())));
        return new ConfigurationChanged(new Identity(actor, tenantId), new Target(QUERY_KEY_TYPE, QUERY_KEY_TYPE_ORDER),
            0, 0, fields, "调整键类型顺序（" + reorder.getAfter().size() + " 项）", now);
    }

    /**
     * 连接测试回写最后一次检查。
     *
     * @param actor 操作人
     * @param tenantId 操作人所属租户
     * @param dataSourceId 数据源 ID
     * @param result PASSED 或 FAILED
     * @param now 写入时间
     * @return 审计事实
     */
    static Outcome connectionTested(String actor, long tenantId, long dataSourceId, String result, Instant now) {
        return Outcome.succeeded(actor, tenantId, new Target(DATA_SOURCE, Long.toString(dataSourceId)),
            "连接测试 " + result, now);
    }

    /** 字段前后值。 */
    static ConfigurationFieldChange field(ConfigurationField field, String before, String after) {
        return new ConfigurationFieldChange(field, before, after);
    }

    /** 连接参数的规范化摘要：引擎、主机、端口、默认库、账号、排序后的驱动参数与连接池上限。 */
    static String connectionDigest(DataSourceDetail detail) {
        DataSourceConnectionConfig connection = detail.getConnection();
        return sha256(String.join("\n", connection.getEngineType().getCode(), connection.getHost(),
            Integer.toString(connection.getPort()), String.valueOf(connection.getDatabaseName()),
            connection.getUsername(), connection.getConnectionParams().toString(),
            Integer.toString(detail.getMaxPoolSize())));
    }

    /** 键类型内容摘要：分组、值类型、默认角色、入口设置与说明。 */
    static String keyTypeDigest(QueryKeyTypeDetail detail) {
        return sha256(String.join("\n", detail.getGroupName(), detail.getValueType().getCode(),
            detail.getDefaultRole().getCode(), Boolean.toString(detail.isEntryAllowed()),
            String.valueOf(detail.getValuePattern()), String.valueOf(detail.getInputHint()),
            String.valueOf(detail.getDescription())));
    }

    /** 顺序摘要：按顺序排列的“编码=顺序号”。 */
    static String orderDigest(List<QueryKeyTypeDetail> keyTypes) {
        return sha256(String.join("\n", keyTypes.stream().map(keyType -> keyType.getKeyTypeCode() + "="
            + keyType.getSortNo()).toList()));
    }

    /** 参数声明摘要。 */
    static String parametersDigest(QueryTemplateDraft content) {
        return sha256(QueryTemplateJson.writeParameters(content.getParameters()));
    }

    /** 列声明、约束与联合查询配置摘要；没有联合查询配置时与此前的摘要相同。 */
    static String definitionDigest(QueryTemplateDraft content) {
        String joint = QueryTemplateJson.writeJoint(content.getJoint());
        return sha256(String.valueOf(QueryTemplateJson.writeColumns(content.getColumns())) + "\n"
            + QueryTemplateJson.writeConstraints(content.getConstraints()) + (joint == null ? "" : "\n" + joint));
    }

    /** 小写十六进制 SHA-256。 */
    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new ErrorException("SHA-256 不可用", failure);
        }
    }
}
