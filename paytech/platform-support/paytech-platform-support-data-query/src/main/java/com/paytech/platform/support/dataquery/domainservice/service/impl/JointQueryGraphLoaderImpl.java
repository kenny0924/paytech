package com.paytech.platform.support.dataquery.domainservice.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.platform.support.dataquery.core.configuration.DataQueryJointProperties;
import com.paytech.platform.support.dataquery.core.dal.dataobject.DataSource;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplate;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplateVersion;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.JointQueryExclusionReason;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.JointQueryExcludedTemplate;
import com.paytech.platform.support.dataquery.domain.JointQueryGraph;
import com.paytech.platform.support.dataquery.domain.JointQueryGroup;
import com.paytech.platform.support.dataquery.domain.JointQueryParticipation;
import com.paytech.platform.support.dataquery.domain.JointQuerySettings;
import com.paytech.platform.support.dataquery.domain.JointQueryTemplate;
import com.paytech.platform.support.dataquery.domain.QueryJointConfig;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateJson;
import com.paytech.platform.support.dataquery.domainservice.repository.DataSourceRepository;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryKeyTypeRepository;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryTemplateRepository;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryTemplateVersionRepository;
import com.paytech.platform.support.dataquery.domainservice.service.JointQueryGraphLoader;
import com.paytech.platform.support.dataquery.template.ParameterDeclarationReader;

import lombok.AllArgsConstructor;

/** 构图：键类型 1 条、模板 1 条、当前版本 1 条、数据源 1 条 SQL；参与条件与泳道位置在内存中计算。 */
@Service
@AllArgsConstructor
public class JointQueryGraphLoaderImpl implements JointQueryGraphLoader {

    private final DataSourceRepository dataSources;
    private final QueryKeyTypeRepository keyTypes;
    private final QueryTemplateRepository templates;
    private final DataQueryJointProperties properties;
    private final QueryTemplateVersionRepository versions;

    /** ACTIVE 且允许作为入口的键类型、泳道分组（来自本次现读的图）与护栏。 */
    @Override
    public JointQuerySettings loadSettings() {
        JointQueryGraph graph = loadGraph();
        List<QueryKeyTypeDetail> entries = graph.getKeyTypes().values().stream()
            .filter(keyType -> keyType.active() && keyType.isEntryAllowed())
            .sorted(Comparator.comparingInt(QueryKeyTypeDetail::getSortNo).thenComparing(QueryKeyTypeDetail::getKeyTypeCode))
            .toList();
        return new JointQuerySettings(entries, graph.getGroups(), new JointQuerySettings.Limits(
            properties.getMaxEntryValues(), properties.getMaxValuesPerKey(), properties.getMaxRowsPerTemplate(),
            properties.getMaxExecutionsPerStep(), properties.getMaxDepth(), properties.getMaxTemplateExecutions(),
            properties.getMaxTotalSeconds()));
    }

    /** 全部模板的当前版本：有联合查询配置且满足参与条件的进入图，其余列为不参与。 */
    @Override
    public JointQueryGraph loadGraph() {
        Map<String, QueryKeyTypeDetail> keyTypeMap = keyTypes.findAll().stream()
            .map(row -> QueryKeyTypeServiceImpl.detail(row, 0))
            .collect(Collectors.toMap(QueryKeyTypeDetail::getKeyTypeCode, Function.identity(), (left, right) -> left,
                TreeMap::new));
        List<JointQueryGroup> groups = groups(List.copyOf(keyTypeMap.values()));
        Map<String, Integer> ranks = groups.stream()
            .collect(Collectors.toMap(JointQueryGroup::getGroupName, JointQueryGroup::getRank));
        List<QueryTemplate> rows = templates.findAll(null);
        Map<Long, QueryTemplateVersion> current = versions.findCurrent(rows);
        Map<Long, DataSource> sources = dataSources.findByIds(current.values().stream()
            .map(QueryTemplateVersion::getDataSourceId).distinct().toList()).stream()
            .collect(Collectors.toMap(DataSource::getDataSourceId, Function.identity()));
        List<JointQueryTemplate> participating = new ArrayList<>();
        List<JointQueryExcludedTemplate> excluded = new ArrayList<>();
        for (QueryTemplate row : rows) {
            QueryTemplateVersion version = current.get(row.getTemplateId());
            QueryJointConfig joint = version == null ? null : QueryTemplateJson.readJoint(version.getJointJson());
            if (joint != null) {
                classify(row, version, joint, sources.get(version.getDataSourceId()), keyTypeMap, ranks, participating,
                    excluded);
            }
        }
        participating.sort(Comparator.comparingInt(JointQueryTemplate::getLaneRank)
            .thenComparingInt(JointQueryTemplate::getSubjectSortNo).thenComparing(JointQueryTemplate::getTemplateCode));
        List<JointQueryGroup> lanes = groups.stream().filter(group -> participating.stream()
            .anyMatch(template -> template.getGroupName().equals(group.getGroupName()))).toList();
        return new JointQueryGraph(keyTypeMap, lanes, List.copyOf(participating), List.copyOf(excluded));
    }

    /** 评估一个有联合查询配置的模板，放入参与或不参与列表。 */
    static void classify(QueryTemplate row, QueryTemplateVersion version, QueryJointConfig joint, DataSource source,
        Map<String, QueryKeyTypeDetail> keyTypes, Map<String, Integer> ranks, List<JointQueryTemplate> participating,
        List<JointQueryExcludedTemplate> excluded) {
        JointQueryParticipation participation = JointQueryParticipation.evaluate(joint,
            new ParameterDeclarationReader().read(QueryTemplateJson.readParameters(version.getParametersJson())),
            row.getStatus() == QueryTemplateStatus.ACTIVE,
            source != null && source.getStatus() == DataSourceStatus.ACTIVE);
        List<String> inactive = joint.referencedKeyTypes().stream()
            .filter(code -> keyTypes.get(code) == null || !keyTypes.get(code).active()).toList();
        if (participation.isParticipating() && !inactive.isEmpty()) {
            participation = JointQueryParticipation.excluded(JointQueryExclusionReason.KEY_TYPE_INACTIVE,
                "不参与联合查询：键类型不存在或未启用 " + String.join(", ", inactive));
        }
        if (!participation.isParticipating()) {
            excluded.add(new JointQueryExcludedTemplate(row.getTemplateCode(), participation.getReason(),
                participation.getMessage()));
            return;
        }
        QueryKeyTypeDetail subject = keyTypes.get(joint.getSubjectKeyType());
        participating.add(JointQueryTemplate.builder().templateCode(row.getTemplateCode())
            .templateName(row.getTemplateName()).versionNo(version.getVersionNo())
            .dataSourceCode(source.getDataSourceCode()).groupName(subject.getGroupName())
            .laneRank(ranks.get(subject.getGroupName())).subjectSortNo(subject.getSortNo())
            .keyParameterList(participation.isKeyParameterList())
            .pagingMode(QueryTemplateJson.readConstraints(version.getConstraintsJson()).getPagingMode()).joint(joint)
            .build());
    }

    /**
     * ACTIVE 键类型的分组：顺序取组内最小 sort_no，相同时按分组名；构图后只保留有参与模板以组内键类型为行主体的分组（泳道）。
     *
     * @param keyTypes 键类型
     * @return 分组
     */
    static List<JointQueryGroup> groups(List<QueryKeyTypeDetail> keyTypes) {
        Map<String, Integer> ranks = new TreeMap<>();
        keyTypes.stream().filter(QueryKeyTypeDetail::active)
            .forEach(keyType -> ranks.merge(keyType.getGroupName(), keyType.getSortNo(), Math::min));
        return ranks.entrySet().stream().map(entry -> new JointQueryGroup(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingInt(JointQueryGroup::getRank).thenComparing(JointQueryGroup::getGroupName))
            .toList();
    }
}
