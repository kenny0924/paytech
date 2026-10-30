package com.paytech.platform.support.ext.rest.dataquery;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateColumn;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateConstraints;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateJointColumn;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateJointConfig;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateJointKeyParameter;
import com.paytech.platform.support.api.dataquery.response.DataQueryColumn;
import com.paytech.platform.support.api.dataquery.response.DataQueryResultResponseBody;
import com.paytech.platform.support.api.dataquery.response.DataSourceConnectionTestResponseBody;
import com.paytech.platform.support.api.dataquery.response.DataSourceResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryParameterSummary;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateDetailResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateJointParticipation;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateProblem;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateSummaryResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateValidationResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateVersionResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateVersionSummaryResponseBody;
import com.paytech.platform.support.api.dataquery.response.SaveQueryTemplateResponseBody;
import com.paytech.platform.support.api.dataquery.response.TrialQueryTemplateResponseBody;
import com.paytech.platform.support.dataquery.core.enums.ColumnMaskType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;
import com.paytech.platform.support.dataquery.domain.DataQueryResult;
import com.paytech.platform.support.dataquery.domain.DataSourceChange;
import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.JointQueryParticipation;
import com.paytech.platform.support.dataquery.domain.QueryColumnRule;
import com.paytech.platform.support.dataquery.domain.QueryConstraints;
import com.paytech.platform.support.dataquery.domain.QueryJointColumn;
import com.paytech.platform.support.dataquery.domain.QueryJointConfig;
import com.paytech.platform.support.dataquery.domain.QueryJointKeyParameter;
import com.paytech.platform.support.dataquery.domain.QueryTemplateChange;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDraft;
import com.paytech.platform.support.dataquery.domain.QueryTemplateSummary;
import com.paytech.platform.support.dataquery.domain.QueryTemplateVersionDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateVersionSummary;
import com.paytech.platform.support.dataquery.service.processor.response.QueryTemplateSaveResult;
import com.paytech.platform.support.dataquery.template.TemplateProblem;
import com.paytech.platform.support.dataquery.template.TemplateValidationResult;

import tools.jackson.databind.JsonNode;

/**
 * 数据查询的协议转换：字符串 ID 与 Code 解析、Body 到领域内容、领域结果到 ResponseBody；不含业务规则。
 * <li>密码与 Vault Token 不进入任何响应。</li>
 */
@Component
public class DataQueryConverter {

    /** 写接口响应的对象类型：数据源。 */
    static final String DATA_SOURCE = "DATA_SOURCE";

    /** 写接口响应的对象类型：模板。 */
    static final String QUERY_TEMPLATE = "QUERY_TEMPLATE";

    private static final DateTimeFormatter MICROS = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    /** 规范正整数字符串 ID；不合法返回 400。 */
    long id(String value, String name) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new InvalidParameterException(name + " 必须为规范正整数字符串");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new InvalidParameterException(name + " 超出范围");
        }
    }

    /** 可选 ID。 */
    Long optionalId(String value, String name) {
        return value == null || value.isBlank() ? null : id(value, name);
    }

    /** 可选枚举 Code；不合法返回 400。 */
    <E extends CodeEnum> E optionalCode(Class<E> type, String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return code(type, value, name);
    }

    /** 必填枚举 Code；不合法返回 400。 */
    <E extends CodeEnum> E code(Class<E> type, String value, String name) {
        E code = EnumUtils.getEnumByCode(type, value);
        if (code == null) {
            throw new InvalidParameterException(name + " 取值不合法: " + value);
        }
        return code;
    }

    /** 分页参数，缺省 page=1、size=10。 */
    PagingRequestBody paging(Integer page, Integer size) {
        return PagingRequestBody.of(page, size);
    }

    /** 去除首尾空白，空白视为 null。 */
    static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** 连接参数；驱动参数白名单由领域对象校验。 */
    DataSourceConnectionConfig connection(String engineType, String host, int port, String databaseName,
        String username, java.util.Map<String, String> params) {
        return new DataSourceConnectionConfig(code(DataSourceEngineType.class, engineType, "engineType"), host, port,
            text(databaseName), username, params);
    }

    /** 草稿内容；试运行不带联合查询配置时 joint 传 null。 */
    QueryTemplateDraft draft(String dataSourceId, String body, JsonNode parameters, List<QueryTemplateColumn> columns,
        QueryTemplateConstraints constraints, QueryTemplateJointConfig joint) {
        return new QueryTemplateDraft(id(dataSourceId, "dataSourceId"), body, parameters,
            columns == null ? List.of() : columns.stream().map(this::column).toList(), constraints(constraints),
            joint(joint));
    }

    /** 联合查询配置；keyRole 已由 Body 注解限定为 LINK / DIMENSION。 */
    QueryJointConfig joint(QueryTemplateJointConfig joint) {
        if (joint == null) {
            return null;
        }
        List<QueryJointColumn> columns = joint.getColumns() == null ? List.of() : joint.getColumns().stream()
            .map(column -> new QueryJointColumn(column.getName(), column.getKeyType(),
                code(QueryKeyRole.class, column.getKeyRole(), "keyRole"))).toList();
        return new QueryJointConfig(joint.getSubjectKeyType(), new QueryJointKeyParameter(
            joint.getKeyParameter().getName(), joint.getKeyParameter().getKeyType()), columns);
    }

    /** 列声明；mask 缺省 NONE，保留位数缺省 0。 */
    QueryColumnRule column(QueryTemplateColumn column) {
        return new QueryColumnRule(column.getName(), text(column.getLabel()),
            column.getMask() == null ? ColumnMaskType.NONE : code(ColumnMaskType.class, column.getMask(), "mask"),
            column.getKeepStart() == null ? 0 : column.getKeepStart(),
            column.getKeepEnd() == null ? 0 : column.getKeepEnd());
    }

    /** 约束；游标列缺省为空。 */
    QueryConstraints constraints(QueryTemplateConstraints constraints) {
        return new QueryConstraints(constraints.getMaxRows(), constraints.getTimeoutSeconds(),
            code(QueryPagingMode.class, constraints.getPagingMode(), "pagingMode"), constraints.getMaxPageSize(),
            constraints.isCountAllowed(),
            constraints.getCursorColumns() == null ? List.of() : List.copyOf(constraints.getCursorColumns()));
    }

    /** 数据源写入结果。 */
    ConfigurationMutationResponseBody mutation(DataSourceChange change) {
        DataSourceDetail after = change.getAfter();
        return new ConfigurationMutationResponseBody(DATA_SOURCE, Long.toString(after.getDataSourceId()),
            after.getDataSourceCode(), after.getStatus().getCode(), after.getVersion());
    }

    /** 模板启停结果。 */
    ConfigurationMutationResponseBody mutation(QueryTemplateChange change) {
        QueryTemplateDetail after = change.getAfter();
        return new ConfigurationMutationResponseBody(QUERY_TEMPLATE, Long.toString(after.getTemplateId()),
            after.getTemplateCode(), after.getStatus().getCode(), after.getVersion());
    }

    /** 数据源，不含密码与 Token。 */
    DataSourceResponseBody dataSource(DataSourceDetail detail) {
        DataSourceResponseBody body = new DataSourceResponseBody();
        body.setDataSourceId(Long.toString(detail.getDataSourceId()));
        body.setDataSourceCode(detail.getDataSourceCode());
        body.setDataSourceName(detail.getDataSourceName());
        body.setEngineType(detail.getConnection().getEngineType().getCode());
        body.setHost(detail.getConnection().getHost());
        body.setPort(detail.getConnection().getPort());
        body.setDatabaseName(detail.getConnection().getDatabaseName());
        body.setUsername(detail.getConnection().getUsername());
        body.setConnectionParams(detail.getConnection().getConnectionParams());
        body.setMaxPoolSize(detail.getMaxPoolSize());
        body.setStatus(detail.getStatus().getCode());
        body.setLastCheckResult(detail.getLastCheckResult() == null ? null : detail.getLastCheckResult().getCode());
        body.setLastCheckMessage(detail.getLastCheckMessage());
        body.setLastCheckTime(time(detail.getLastCheckTime()));
        body.setDescription(detail.getDescription());
        body.setTemplateCount(detail.getTemplateCount());
        body.setActiveTemplateCount(detail.getActiveTemplateCount());
        body.setVersion(detail.getVersion());
        body.setCreatedBy(Long.toString(detail.getCreatedBy()));
        body.setUpdatedBy(Long.toString(detail.getUpdatedBy()));
        body.setCreateTime(time(detail.getCreateTime()));
        body.setUpdateTime(time(detail.getUpdateTime()));
        return body;
    }

    /** 连接检查结果。 */
    DataSourceConnectionTestResponseBody connectionTest(ConnectionCheckResult result, Instant checkedAt) {
        DataSourceConnectionTestResponseBody body = new DataSourceConnectionTestResponseBody();
        body.setResult(result.result().getCode());
        body.setConnected(result.isConnected());
        body.setPrivilegesPassed(result.privilegesPassed());
        body.setGrants(result.getGrants());
        body.setViolations(result.getViolations());
        body.setCheckedAt(time(checkedAt));
        return body;
    }

    /** 模板列表项。 */
    QueryTemplateSummaryResponseBody templateSummary(QueryTemplateSummary summary) {
        QueryTemplateDetail template = summary.getTemplate();
        QueryTemplateSummaryResponseBody body = new QueryTemplateSummaryResponseBody();
        body.setTemplateId(Long.toString(template.getTemplateId()));
        body.setTemplateCode(template.getTemplateCode());
        body.setTemplateName(template.getTemplateName());
        body.setDescription(template.getDescription());
        body.setDataSourceId(Long.toString(summary.getDataSourceId()));
        body.setDataSourceCode(summary.getDataSourceCode());
        body.setDataSourceName(summary.getDataSourceName());
        body.setStatus(template.getStatus().getCode());
        body.setCurrentVersionNo(template.getCurrentVersionNo());
        body.setPagingMode(summary.getPagingMode() == null ? null : summary.getPagingMode().getCode());
        body.setParameters(summary.getParameters().stream().map(parameter -> {
            QueryParameterSummary item = new QueryParameterSummary();
            item.setName(parameter.getName());
            item.setType(parameter.getType().getCode());
            item.setElementType(parameter.getElementType() == null ? null : parameter.getElementType().getCode());
            item.setRequired(parameter.isRequired());
            return item;
        }).toList());
        body.setVersion(template.getVersion());
        body.setUpdatedBy(Long.toString(template.getUpdatedBy()));
        body.setUpdateTime(time(template.getUpdateTime()));
        return body;
    }

    /** 模板详情。 */
    QueryTemplateDetailResponseBody templateDetail(QueryTemplateDetail template) {
        QueryTemplateDetailResponseBody body = new QueryTemplateDetailResponseBody();
        body.setTemplateId(Long.toString(template.getTemplateId()));
        body.setTemplateCode(template.getTemplateCode());
        body.setTemplateName(template.getTemplateName());
        body.setDescription(template.getDescription());
        body.setStatus(template.getStatus().getCode());
        body.setCurrentVersionNo(template.getCurrentVersionNo());
        body.setVersion(template.getVersion());
        body.setCreatedBy(Long.toString(template.getCreatedBy()));
        body.setUpdatedBy(Long.toString(template.getUpdatedBy()));
        body.setCreateTime(time(template.getCreateTime()));
        body.setUpdateTime(time(template.getUpdateTime()));
        body.setCurrentVersion(version(template.getCurrentVersion()));
        body.setJoint(body.getCurrentVersion().getJoint());
        body.setJointQuery(body.getCurrentVersion().getJointQuery());
        return body;
    }

    /** 版本内容。 */
    QueryTemplateVersionResponseBody version(QueryTemplateVersionDetail version) {
        QueryTemplateVersionResponseBody body = new QueryTemplateVersionResponseBody();
        body.setTemplateId(Long.toString(version.getTemplateId()));
        body.setVersionNo(version.getVersionNo());
        body.setDataSourceId(Long.toString(version.getDataSourceId()));
        body.setDataSourceCode(version.getDataSourceCode());
        body.setDataSourceName(version.getDataSourceName());
        body.setDataSourceStatus(version.getDataSourceStatus() == null ? null : version.getDataSourceStatus().getCode());
        body.setBody(version.getContent().getBody());
        body.setParameters(version.getContent().getParameters());
        body.setColumns(version.getContent().getColumns().stream().map(DataQueryConverter::columnBody).toList());
        body.setConstraints(constraintsBody(version.getContent().getConstraints()));
        body.setJoint(jointBody(version.getContent().getJoint()));
        body.setJointQuery(participationBody(version.getJointParticipation()));
        body.setRestoredFromVersionNo(version.getRestoredFromVersionNo());
        body.setChangeNote(version.getChangeNote());
        body.setCreatedBy(Long.toString(version.getCreatedBy()));
        body.setCreateTime(time(version.getCreateTime()));
        return body;
    }

    /** 版本列表项。 */
    QueryTemplateVersionSummaryResponseBody versionSummary(QueryTemplateVersionSummary summary) {
        QueryTemplateVersionSummaryResponseBody body = new QueryTemplateVersionSummaryResponseBody();
        body.setVersionNo(summary.getVersionNo());
        body.setDataSourceId(Long.toString(summary.getDataSourceId()));
        body.setDataSourceCode(summary.getDataSourceCode());
        body.setRestoredFromVersionNo(summary.getRestoredFromVersionNo());
        body.setChangeNote(summary.getChangeNote());
        body.setCreatedBy(Long.toString(summary.getCreatedBy()));
        body.setCreateTime(time(summary.getCreateTime()));
        return body;
    }

    /** 模板保存结果。 */
    SaveQueryTemplateResponseBody save(QueryTemplateSaveResult result) {
        QueryTemplateDetail after = result.getChange().getAfter();
        SaveQueryTemplateResponseBody body = new SaveQueryTemplateResponseBody();
        body.setTemplateId(Long.toString(after.getTemplateId()));
        body.setTemplateCode(after.getTemplateCode());
        body.setStatus(after.getStatus().getCode());
        body.setVersion(after.getVersion());
        body.setCurrentVersionNo(after.getCurrentVersionNo());
        body.setWarnings(result.getWarnings().stream().map(DataQueryConverter::problem).toList());
        return body;
    }

    /** 校验结果。 */
    QueryTemplateValidationResponseBody validation(TemplateValidationResult result) {
        QueryTemplateValidationResponseBody body = new QueryTemplateValidationResponseBody();
        body.setValid(!result.hasErrors());
        body.setProblems(result.getProblems().stream().map(DataQueryConverter::problem).toList());
        return body;
    }

    /** 执行结果。 */
    DataQueryResultResponseBody result(DataQueryResult result) {
        DataQueryResultResponseBody body = new DataQueryResultResponseBody();
        body.setVersionNo(result.getVersionNo());
        body.setColumns(columns(result));
        body.setRows(result.getRows());
        body.setHasMore(result.isHasMore());
        body.setTotal(result.getTotal());
        body.setNextCursor(result.getNextCursor());
        body.setElapsedMillis(result.getElapsedMillis());
        return body;
    }

    /** 试运行结果，附实际执行的 SQL 与绑定值。 */
    TrialQueryTemplateResponseBody trial(DataQueryResult result) {
        TrialQueryTemplateResponseBody body = new TrialQueryTemplateResponseBody();
        body.setVersionNo(result.getVersionNo());
        body.setColumns(columns(result));
        body.setRows(result.getRows());
        body.setHasMore(result.isHasMore());
        body.setTotal(result.getTotal());
        body.setNextCursor(result.getNextCursor());
        body.setElapsedMillis(result.getElapsedMillis());
        body.setSql(result.getSql());
        body.setBindings(result.getBindings());
        body.setCountSql(result.getCountSql());
        return body;
    }

    /** 结果列。 */
    static List<DataQueryColumn> columns(DataQueryResult result) {
        return result.getColumns().stream().map(column -> {
            DataQueryColumn body = new DataQueryColumn();
            body.setName(column.getName());
            body.setLabel(column.getLabel());
            return body;
        }).toList();
    }

    /** 校验问题。 */
    static QueryTemplateProblem problem(TemplateProblem problem) {
        QueryTemplateProblem body = new QueryTemplateProblem();
        body.setLevel(problem.getLevel().getCode());
        body.setLine(problem.getLine());
        body.setColumn(problem.getColumn());
        body.setField(problem.getField());
        body.setMessage(problem.getMessage());
        return body;
    }

    /** 列声明 DTO。 */
    static QueryTemplateColumn columnBody(QueryColumnRule rule) {
        QueryTemplateColumn body = new QueryTemplateColumn();
        body.setName(rule.getName());
        body.setLabel(rule.getLabel());
        body.setMask(rule.getMask().getCode());
        body.setKeepStart(rule.getKeepStart());
        body.setKeepEnd(rule.getKeepEnd());
        return body;
    }

    /** 联合查询配置 DTO；不参与时为 null。 */
    static QueryTemplateJointConfig jointBody(QueryJointConfig joint) {
        if (joint == null) {
            return null;
        }
        QueryTemplateJointConfig body = new QueryTemplateJointConfig();
        body.setSubjectKeyType(joint.getSubjectKeyType());
        QueryTemplateJointKeyParameter key = new QueryTemplateJointKeyParameter();
        key.setName(joint.getKeyParameter().getName());
        key.setKeyType(joint.getKeyParameter().getKeyType());
        body.setKeyParameter(key);
        body.setColumns(joint.getColumns().stream().map(column -> {
            QueryTemplateJointColumn item = new QueryTemplateJointColumn();
            item.setName(column.getName());
            item.setKeyType(column.getKeyType());
            item.setKeyRole(column.getKeyRole().getCode());
            return item;
        }).toList());
        return body;
    }

    /** 联合查询参与情况 DTO；没有联合查询配置时为 null。 */
    static QueryTemplateJointParticipation participationBody(JointQueryParticipation participation) {
        if (participation == null) {
            return null;
        }
        QueryTemplateJointParticipation body = new QueryTemplateJointParticipation();
        body.setParticipating(participation.isParticipating());
        body.setReasonCode(participation.getReason() == null ? null : participation.getReason().getCode());
        body.setReason(participation.getMessage());
        return body;
    }

    /** 约束 DTO。 */
    static QueryTemplateConstraints constraintsBody(QueryConstraints constraints) {
        QueryTemplateConstraints body = new QueryTemplateConstraints();
        body.setMaxRows(constraints.getMaxRows());
        body.setTimeoutSeconds(constraints.getTimeoutSeconds());
        body.setPagingMode(constraints.getPagingMode().getCode());
        body.setMaxPageSize(constraints.getMaxPageSize());
        body.setCountAllowed(constraints.isCountAllowed());
        body.setCursorColumns(constraints.getCursorColumns());
        return body;
    }

    /** UTC 微秒 ISO-8601；null 为 null。 */
    static String time(Instant instant) {
        return instant == null ? null : MICROS.format(instant);
    }
}
