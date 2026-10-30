package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 联合查询页初始化数据：入口键类型、泳道分组与护栏。 */
@Getter
@AllArgsConstructor
public final class JointQuerySettings {

    /** ACTIVE 且允许作为入口的键类型，按 sort_no、编码升序。 */
    private final List<QueryKeyTypeDetail> entryKeyTypes;

    /** 泳道分组（有参与模板以组内键类型为行主体），按泳道顺序升序；只作维度的分组不出现。 */
    private final List<JointQueryGroup> groups;

    /** 护栏。 */
    private final Limits limits;

    /** 护栏取值。 */
    @Getter
    @AllArgsConstructor
    public static final class Limits {

        /** 入口值个数上限。 */
        private final int maxEntryValues;

        /** 单键类型的值个数上限。 */
        private final int maxValuesPerKey;

        /** 每个模板执行的行数上限。 */
        private final int maxRowsPerTemplate;

        /** 单步模板执行数上限。 */
        private final int maxExecutionsPerStep;

        /** 最大层数。 */
        private final int maxDepth;

        /** 一次联合查询的模板执行总数上限。 */
        private final int maxTemplateExecutions;

        /** 一次联合查询的总时长上限秒数。 */
        private final int maxTotalSeconds;
    }
}
