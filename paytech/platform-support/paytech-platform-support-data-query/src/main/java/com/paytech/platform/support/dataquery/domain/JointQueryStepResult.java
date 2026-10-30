package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import com.paytech.platform.support.dataquery.core.enums.JointQueryNodeStatus;
import com.paytech.platform.support.dataquery.core.enums.JointQueryTruncationRule;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 联合查询一步的结果：本步节点、各结果行的链路值、按键类型去重的新发现键、服务端截断与因已加载而未执行的视图。 */
@Getter
@AllArgsConstructor
public final class JointQueryStepResult {

    /** 层号。 */
    private final int depth;

    /** 节点，按任务生成顺序。 */
    private final List<Node> nodes;

    /** 链路值：每个成功节点结果行中 LINK 列的非空规范值。 */
    private final List<Link> links;

    /** 本步链路值按键类型去重，每键类型最多 maxValuesPerKey 个。 */
    private final List<JointQueryKey> discoveredKeys;

    /** 服务端单步截断。 */
    private final List<Truncation> truncations;

    /** 因对象已加载而未执行（或未用全部值执行）的“按自身 ID 查”视图，按任务生成顺序。 */
    private final List<AlreadyLoaded> alreadyLoaded;

    /** 本步耗时毫秒。 */
    private final long elapsedMillis;

    /** 一次模板执行。 */
    @Getter
    @Builder
    public static final class Node {

        /** 节点 ID：depth:templateCode:triggerKeyType，标量键参数逐值执行时追加 :序号。 */
        private final String nodeId;

        /** 模板。 */
        private final JointQueryTemplate template;

        /** 实际执行的版本号；未执行时为构图时的当前版本号。 */
        private final int versionNo;

        /** 本次绑定到键参数的值。 */
        private final List<String> triggerValues;

        /** 结果。 */
        private final JointQueryNodeStatus status;

        /** FAILED / SKIPPED 的原因码；成功为 null。 */
        private final String errorCode;

        /** FAILED / SKIPPED 的安全中文说明；成功为 null。 */
        private final String errorMessage;

        /** 是否还有更多行未返回。 */
        private final boolean truncated;

        /** 游标分页且还有更多行时的下一页游标。 */
        private final String nextCursor;

        /** 执行耗时毫秒；SKIPPED 为 0。 */
        private final long elapsedMillis;

        /** 提示，如 LINK_COLUMN_MISSING:order_id。 */
        private final List<String> warnings;

        /** 结果列；FAILED / SKIPPED 为空。 */
        private final List<Column> columns;

        /** 结果行（已屏蔽）；FAILED / SKIPPED 为空。 */
        private final List<List<Object>> rows;
    }

    /** 结果列及其联合查询标注。 */
    @Getter
    @AllArgsConstructor
    public static final class Column {

        /** 列名。 */
        private final String name;

        /** 显示标题。 */
        private final String label;

        /** 键类型编码；未标注时为 null。 */
        private final String keyType;

        /** 角色；未标注时为 null。 */
        private final QueryKeyRole keyRole;
    }

    /** 一个链路值。 */
    @Getter
    @AllArgsConstructor
    public static final class Link {

        /** 来源节点 ID。 */
        private final String nodeId;

        /** 来源行下标，从 0 开始。 */
        private final int rowIndex;

        /** 来源列名。 */
        private final String column;

        /** 键类型编码。 */
        private final String keyType;

        /** 规范值。 */
        private final String value;
    }

    /** 一个因已加载而未执行的视图。 */
    @Getter
    @AllArgsConstructor
    public static final class AlreadyLoaded {

        /** 模板。 */
        private final JointQueryTemplate template;

        /** 触发键类型，等于模板的行主体键类型。 */
        private final String keyType;

        /** 因已加载而未用于执行的规范值，保持输入顺序。 */
        private final List<String> values;
    }

    /** 一项截断。 */
    @Getter
    @AllArgsConstructor
    public static final class Truncation {

        /** 触发项。 */
        private final JointQueryTruncationRule rule;

        /** MAX_VALUES_PER_KEY 的键类型；其他为 null。 */
        private final String keyType;

        /** 丢弃的值数或跳过的执行数。 */
        private final int dropped;
    }
}
