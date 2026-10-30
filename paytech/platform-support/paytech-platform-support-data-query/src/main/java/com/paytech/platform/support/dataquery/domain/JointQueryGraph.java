package com.paytech.platform.support.dataquery.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 每次请求现读的键类型图：全部键类型、泳道分组、参与模板与不参与模板（technique 8.2、S-04 不缓存）。
 * <li>展开只沿参与模板的 LINK 列；DIMENSION 列不展开。纯内存对象，不访问数据库。</li>
 */
@Getter
@AllArgsConstructor
public final class JointQueryGraph {

    /** 泳道内与预览中模板的顺序。 */
    static final Comparator<JointQueryTemplate> LANE_ORDER = Comparator.comparingInt(JointQueryTemplate::getLaneRank)
        .thenComparingInt(JointQueryTemplate::getSubjectSortNo).thenComparing(JointQueryTemplate::getTemplateCode);

    /** 全部键类型，按编码。 */
    private final Map<String, QueryKeyTypeDetail> keyTypes;

    /** 泳道分组：至少一个参与模板的行主体键类型所在分组，按组内 ACTIVE 键类型最小 sort_no 排序。 */
    private final List<JointQueryGroup> groups;

    /** 参与模板，按泳道顺序、行主体 sort_no、编码升序。 */
    private final List<JointQueryTemplate> templates;

    /** 不参与的模板，按编码升序。 */
    private final List<JointQueryExcludedTemplate> excluded;

    /**
     * 按编码查找键类型。
     *
     * @param code 编码
     * @return 键类型；不存在时为 null
     */
    public QueryKeyTypeDetail keyType(String code) {
        return keyTypes.get(code);
    }

    /**
     * 以某键类型为触发键的参与模板，按泳道顺序。
     *
     * @param keyType 键类型编码
     * @return 模板
     */
    public List<JointQueryTemplate> triggeredBy(String keyType) {
        return templates.stream().filter(template -> template.triggerKeyType().equals(keyType)).toList();
    }

    /**
     * 从入口键类型按层（广度优先）推算可达模板与断点；入口须已校验为 ACTIVE 且允许作为入口。
     *
     * @param entryKeyType 入口键类型编码
     * @return 预览
     */
    public JointQueryPreview preview(String entryKeyType) {
        Map<String, Integer> distances = new LinkedHashMap<>();
        Map<String, Integer> reached = new LinkedHashMap<>();
        List<JointQueryPreview.Break> breaks = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>(List.of(entryKeyType));
        distances.put(entryKeyType, 0);
        while (!queue.isEmpty()) {
            String keyType = queue.poll();
            int distance = distances.get(keyType);
            List<JointQueryTemplate> triggered = triggeredBy(keyType);
            if (triggered.isEmpty()) {
                breaks.add(new JointQueryPreview.Break(keyType, "没有参与模板以 " + keyType + " 为触发键，链路在此断开"));
            }
            for (JointQueryTemplate template : triggered) {
                reached.putIfAbsent(template.getTemplateCode(), distance);
                template.getJoint().linkKeyTypes().stream().filter(this::isActive)
                    .filter(link -> distances.putIfAbsent(link, distance + 1) == null).forEach(queue::add);
            }
        }
        return new JointQueryPreview(previewGroups(reached), List.copyOf(breaks), excluded);
    }

    /** 可达模板按泳道分组。 */
    List<JointQueryPreview.Group> previewGroups(Map<String, Integer> reached) {
        Map<String, List<JointQueryPreview.Entry>> lanes = new LinkedHashMap<>();
        templates.stream().filter(template -> reached.containsKey(template.getTemplateCode()))
            .sorted(Comparator.comparingInt(JointQueryTemplate::getLaneRank)
                .thenComparingInt(JointQueryTemplate::getSubjectSortNo)
                .thenComparing(template -> reached.get(template.getTemplateCode()))
                .thenComparing(JointQueryTemplate::getTemplateCode))
            .forEach(template -> lanes.computeIfAbsent(template.getGroupName(), name -> new ArrayList<>())
                .add(new JointQueryPreview.Entry(template, reached.get(template.getTemplateCode()))));
        return groups.stream().filter(group -> lanes.containsKey(group.getGroupName()))
            .map(group -> new JointQueryPreview.Group(group.getGroupName(), group.getRank(),
                List.copyOf(lanes.get(group.getGroupName())))).toList();
    }

    /** 键类型存在且 ACTIVE。 */
    boolean isActive(String code) {
        QueryKeyTypeDetail keyType = keyTypes.get(code);
        return keyType != null && keyType.active();
    }
}
