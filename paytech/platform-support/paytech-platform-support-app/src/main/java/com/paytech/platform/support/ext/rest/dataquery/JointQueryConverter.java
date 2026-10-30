package com.paytech.platform.support.ext.rest.dataquery;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.dataquery.request.CreateQueryKeyTypeRequestBody;
import com.paytech.platform.support.api.dataquery.request.JointQueryKeyValues;
import com.paytech.platform.support.api.dataquery.request.JointQueryLoadedSubject;
import com.paytech.platform.support.api.dataquery.request.QueryKeyTypeOrderItem;
import com.paytech.platform.support.api.dataquery.request.UpdateQueryKeyTypeRequestBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryAlreadyLoaded;
import com.paytech.platform.support.api.dataquery.response.JointQueryEntryKeyType;
import com.paytech.platform.support.api.dataquery.response.JointQueryExcludedTemplate;
import com.paytech.platform.support.api.dataquery.response.JointQueryGraphBreak;
import com.paytech.platform.support.api.dataquery.response.JointQueryGraphGroup;
import com.paytech.platform.support.api.dataquery.response.JointQueryGraphResponseBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryGraphTemplate;
import com.paytech.platform.support.api.dataquery.response.JointQueryGroup;
import com.paytech.platform.support.api.dataquery.response.JointQueryLimits;
import com.paytech.platform.support.api.dataquery.response.JointQueryLink;
import com.paytech.platform.support.api.dataquery.response.JointQueryNode;
import com.paytech.platform.support.api.dataquery.response.JointQueryNodeColumn;
import com.paytech.platform.support.api.dataquery.response.JointQuerySettingsResponseBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryStepResponseBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryTruncation;
import com.paytech.platform.support.api.dataquery.response.QueryKeyTypeOrderResult;
import com.paytech.platform.support.api.dataquery.response.QueryKeyTypeResponseBody;
import com.paytech.platform.support.api.dataquery.response.ReorderQueryKeyTypesResponseBody;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;
import com.paytech.platform.support.dataquery.domain.JointQueryKey;
import com.paytech.platform.support.dataquery.domain.JointQueryPreview;
import com.paytech.platform.support.dataquery.domain.JointQuerySettings;
import com.paytech.platform.support.dataquery.domain.JointQueryStepResult;
import com.paytech.platform.support.dataquery.domain.JointQueryTemplate;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeChange;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDefinition;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeReorder;
import com.paytech.platform.support.dataquery.domain.ReorderQueryKeyTypesCommand;

import lombok.AllArgsConstructor;

/** 键类型与联合查询的协议转换：Body 到领域输入、领域结果到 ResponseBody；空集合一律输出 []，不含业务规则。 */
@Component
@AllArgsConstructor
public class JointQueryConverter {

    /** 写接口响应的对象类型：键类型。 */
    static final String QUERY_KEY_TYPE = "QUERY_KEY_TYPE";

    private final DataQueryConverter converter;

    /** 新建内容；文本去除首尾空白，空白视为 null。 */
    QueryKeyTypeDefinition definition(CreateQueryKeyTypeRequestBody body) {
        return definition(body.getKeyTypeName(), body.getGroupName(), body.getValueType(), body.getDefaultRole(),
            body.getEntryAllowed(), body.getValuePattern(), body.getInputHint(), body.getSortNo(),
            body.getDescription());
    }

    /** 修改内容；文本去除首尾空白，空白视为 null。 */
    QueryKeyTypeDefinition definition(UpdateQueryKeyTypeRequestBody body) {
        return definition(body.getKeyTypeName(), body.getGroupName(), body.getValueType(), body.getDefaultRole(),
            body.getEntryAllowed(), body.getValuePattern(), body.getInputHint(), body.getSortNo(),
            body.getDescription());
    }

    /** 可维护内容；正则保留原文（不 trim），只把空串视为不校验。 */
    QueryKeyTypeDefinition definition(String name, String group, String valueType, String defaultRole,
        Boolean entryAllowed, String valuePattern, String inputHint, Integer sortNo, String description) {
        return new QueryKeyTypeDefinition(DataQueryConverter.text(name), DataQueryConverter.text(group),
            converter.code(QueryKeyValueType.class, valueType, "valueType"),
            converter.code(QueryKeyRole.class, defaultRole, "defaultRole"), Boolean.TRUE.equals(entryAllowed),
            valuePattern == null || valuePattern.isEmpty() ? null : valuePattern, DataQueryConverter.text(inputHint),
            sortNo, DataQueryConverter.text(description));
    }

    /** 重排项。 */
    List<ReorderQueryKeyTypesCommand.Item> orderItems(List<QueryKeyTypeOrderItem> items) {
        return items.stream().map(item -> new ReorderQueryKeyTypesCommand.Item(
            converter.id(item.getKeyTypeId(), "keyTypeId"), item.getExpectedVersion())).toList();
    }

    /** 本层键值。 */
    static List<JointQueryKey> keys(List<JointQueryKeyValues> keys) {
        return keys.stream().map(key -> new JointQueryKey(key.getKeyType(), List.copyOf(key.getValues()))).toList();
    }

    /** 已加载对象；为 null 时返回 null。 */
    static List<JointQueryKey> loadedSubjects(List<JointQueryLoadedSubject> loaded) {
        return loaded == null ? null : loaded.stream()
            .map(item -> new JointQueryKey(item.getKeyType(), List.copyOf(item.getValues()))).toList();
    }

    /** 键类型写入结果。 */
    static ConfigurationMutationResponseBody mutation(QueryKeyTypeChange change) {
        QueryKeyTypeDetail after = change.getAfter();
        return new ConfigurationMutationResponseBody(QUERY_KEY_TYPE, Long.toString(after.getKeyTypeId()),
            after.getKeyTypeCode(), after.getStatus().getCode(), after.getVersion());
    }

    /** 键类型。 */
    static QueryKeyTypeResponseBody keyType(QueryKeyTypeDetail detail) {
        QueryKeyTypeResponseBody body = new QueryKeyTypeResponseBody();
        body.setKeyTypeId(Long.toString(detail.getKeyTypeId()));
        body.setKeyTypeCode(detail.getKeyTypeCode());
        body.setKeyTypeName(detail.getKeyTypeName());
        body.setGroupName(detail.getGroupName());
        body.setValueType(detail.getValueType().getCode());
        body.setDefaultRole(detail.getDefaultRole().getCode());
        body.setEntryAllowed(detail.isEntryAllowed());
        body.setValuePattern(detail.getValuePattern());
        body.setInputHint(detail.getInputHint());
        body.setSortNo(detail.getSortNo());
        body.setDescription(detail.getDescription());
        body.setStatus(detail.getStatus().getCode());
        body.setVersion(detail.getVersion());
        body.setReferencedTemplateCount(detail.getReferencedTemplateCount());
        body.setUpdatedBy(Long.toString(detail.getUpdatedBy()));
        body.setUpdateTime(DataQueryConverter.time(detail.getUpdateTime()));
        return body;
    }

    /** 重排结果，按新顺序。 */
    static ReorderQueryKeyTypesResponseBody reorder(QueryKeyTypeReorder reorder) {
        ReorderQueryKeyTypesResponseBody body = new ReorderQueryKeyTypesResponseBody();
        body.setItems(reorder.getAfter().stream().map(keyType -> {
            QueryKeyTypeOrderResult item = new QueryKeyTypeOrderResult();
            item.setKeyTypeId(Long.toString(keyType.getKeyTypeId()));
            item.setSortNo(keyType.getSortNo());
            item.setVersion(keyType.getVersion());
            return item;
        }).toList());
        return body;
    }

    /** 页面初始化数据。 */
    static JointQuerySettingsResponseBody settings(JointQuerySettings settings) {
        JointQuerySettingsResponseBody body = new JointQuerySettingsResponseBody();
        body.setEntryKeyTypes(settings.getEntryKeyTypes().stream().map(keyType -> {
            JointQueryEntryKeyType item = new JointQueryEntryKeyType();
            item.setKeyTypeCode(keyType.getKeyTypeCode());
            item.setKeyTypeName(keyType.getKeyTypeName());
            item.setGroupName(keyType.getGroupName());
            item.setValueType(keyType.getValueType().getCode());
            item.setValuePattern(keyType.getValuePattern());
            item.setInputHint(keyType.getInputHint());
            item.setSortNo(keyType.getSortNo());
            return item;
        }).toList());
        body.setGroups(settings.getGroups().stream().map(group -> {
            JointQueryGroup item = new JointQueryGroup();
            item.setGroupName(group.getGroupName());
            item.setRank(group.getRank());
            return item;
        }).toList());
        body.setLimits(limits(settings.getLimits()));
        return body;
    }

    /** 护栏。 */
    static JointQueryLimits limits(JointQuerySettings.Limits limits) {
        JointQueryLimits body = new JointQueryLimits();
        body.setMaxEntryValues(limits.getMaxEntryValues());
        body.setMaxValuesPerKey(limits.getMaxValuesPerKey());
        body.setMaxRowsPerTemplate(limits.getMaxRowsPerTemplate());
        body.setMaxExecutionsPerStep(limits.getMaxExecutionsPerStep());
        body.setMaxDepth(limits.getMaxDepth());
        body.setMaxTemplateExecutions(limits.getMaxTemplateExecutions());
        body.setMaxTotalSeconds(limits.getMaxTotalSeconds());
        return body;
    }

    /** 可达预览。 */
    static JointQueryGraphResponseBody graph(JointQueryPreview preview) {
        JointQueryGraphResponseBody body = new JointQueryGraphResponseBody();
        body.setGroups(preview.getGroups().stream().map(group -> {
            JointQueryGraphGroup item = new JointQueryGraphGroup();
            item.setGroupName(group.getGroupName());
            item.setRank(group.getRank());
            item.setTemplates(group.getTemplates().stream().map(JointQueryConverter::graphTemplate).toList());
            return item;
        }).toList());
        body.setBreaks(preview.getBreaks().stream().map(item -> {
            JointQueryGraphBreak itemBody = new JointQueryGraphBreak();
            itemBody.setKeyType(item.getKeyType());
            itemBody.setReason(item.getReason());
            return itemBody;
        }).toList());
        body.setExcluded(preview.getExcluded().stream().map(item -> {
            JointQueryExcludedTemplate itemBody = new JointQueryExcludedTemplate();
            itemBody.setTemplateCode(item.getTemplateCode());
            itemBody.setReasonCode(item.getReason().getCode());
            itemBody.setReason(item.getMessage());
            return itemBody;
        }).toList());
        return body;
    }

    /** 预览中的一个可达模板。 */
    static JointQueryGraphTemplate graphTemplate(JointQueryPreview.Entry entry) {
        JointQueryTemplate template = entry.getTemplate();
        JointQueryGraphTemplate body = new JointQueryGraphTemplate();
        body.setTemplateCode(template.getTemplateCode());
        body.setTemplateName(template.getTemplateName());
        body.setDataSourceCode(template.getDataSourceCode());
        body.setSubjectKeyType(template.getJoint().getSubjectKeyType());
        body.setTriggerKeyType(template.triggerKeyType());
        body.setMinSteps(entry.getMinSteps());
        body.setLinkKeyTypes(template.getJoint().linkKeyTypes());
        return body;
    }

    /** 一步的结果。 */
    static JointQueryStepResponseBody step(JointQueryStepResult result) {
        JointQueryStepResponseBody body = new JointQueryStepResponseBody();
        body.setDepth(result.getDepth());
        body.setNodes(result.getNodes().stream().map(JointQueryConverter::node).toList());
        body.setLinks(result.getLinks().stream().map(link -> {
            JointQueryLink item = new JointQueryLink();
            item.setNodeId(link.getNodeId());
            item.setRowIndex(link.getRowIndex());
            item.setColumn(link.getColumn());
            item.setKeyType(link.getKeyType());
            item.setValue(link.getValue());
            return item;
        }).toList());
        body.setDiscoveredKeys(result.getDiscoveredKeys().stream().map(key -> {
            JointQueryKeyValues item = new JointQueryKeyValues();
            item.setKeyType(key.getKeyType());
            item.setValues(key.getValues());
            return item;
        }).toList());
        body.setTruncations(result.getTruncations().stream().map(truncation -> {
            JointQueryTruncation item = new JointQueryTruncation();
            item.setRule(truncation.getRule().getCode());
            item.setKeyType(truncation.getKeyType());
            item.setDropped(truncation.getDropped());
            return item;
        }).toList());
        body.setAlreadyLoaded(result.getAlreadyLoaded().stream().map(skipped -> {
            JointQueryAlreadyLoaded item = new JointQueryAlreadyLoaded();
            item.setTemplateCode(skipped.getTemplate().getTemplateCode());
            item.setTemplateName(skipped.getTemplate().getTemplateName());
            item.setKeyType(skipped.getKeyType());
            item.setValues(skipped.getValues());
            return item;
        }).toList());
        body.setElapsedMillis(result.getElapsedMillis());
        return body;
    }

    /** 一个节点。 */
    static JointQueryNode node(JointQueryStepResult.Node node) {
        JointQueryTemplate template = node.getTemplate();
        JointQueryNode body = new JointQueryNode();
        body.setNodeId(node.getNodeId());
        body.setTemplateCode(template.getTemplateCode());
        body.setTemplateName(template.getTemplateName());
        body.setVersionNo(node.getVersionNo());
        body.setDataSourceCode(template.getDataSourceCode());
        body.setSubjectKeyType(template.getJoint().getSubjectKeyType());
        body.setGroupName(template.getGroupName());
        body.setLaneRank(template.getLaneRank());
        body.setSubjectSortNo(template.getSubjectSortNo());
        body.setTriggerKeyType(template.triggerKeyType());
        body.setKeyParameter(template.getJoint().getKeyParameter().getName());
        body.setKeyParameterList(template.isKeyParameterList());
        body.setTriggerValues(node.getTriggerValues());
        body.setStatus(node.getStatus().getCode());
        body.setErrorCode(node.getErrorCode());
        body.setErrorMessage(node.getErrorMessage());
        body.setPagingMode(template.getPagingMode().getCode());
        body.setTruncated(node.isTruncated());
        body.setNextCursor(node.getNextCursor());
        body.setElapsedMillis(node.getElapsedMillis());
        body.setWarnings(node.getWarnings());
        body.setColumns(node.getColumns().stream().map(column -> {
            JointQueryNodeColumn item = new JointQueryNodeColumn();
            item.setName(column.getName());
            item.setLabel(column.getLabel());
            item.setKeyType(column.getKeyType());
            item.setKeyRole(column.getKeyRole() == null ? null : column.getKeyRole().getCode());
            return item;
        }).toList());
        body.setRows(node.getRows());
        return body;
    }
}
