package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 从入口键类型出发的可达预览：按泳道分组的可达模板（最少步数）、链路断点与不参与的模板。 */
@Getter
@AllArgsConstructor
public final class JointQueryPreview {

    /** 有可达模板的分组，按泳道顺序。 */
    private final List<Group> groups;

    /** 可到达但没有参与模板以它为触发键的键类型。 */
    private final List<Break> breaks;

    /** 有联合查询配置却不参与的模板，不限于可达范围。 */
    private final List<JointQueryExcludedTemplate> excluded;

    /** 一个泳道。 */
    @Getter
    @AllArgsConstructor
    public static final class Group {

        /** 分组显示名。 */
        private final String groupName;

        /** 泳道顺序。 */
        private final int rank;

        /** 可达模板，按行主体 sort_no、最少步数、编码升序。 */
        private final List<Entry> templates;
    }

    /** 一个可达模板。 */
    @Getter
    @AllArgsConstructor
    public static final class Entry {

        /** 模板。 */
        private final JointQueryTemplate template;

        /** 最少步数：入口直接触发为 0。 */
        private final int minSteps;
    }

    /** 一个链路断点。 */
    @Getter
    @AllArgsConstructor
    public static final class Break {

        /** 键类型编码。 */
        private final String keyType;

        /** 中文说明。 */
        private final String reason;
    }
}
