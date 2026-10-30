package com.paytech.platform.support.api.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一层中的一次模板执行（模板 × 触发键类型；标量键参数时每个值一个节点）。 */
@Getter
@Setter
public class JointQueryNode {

    /**
     * 节点 ID
     * <li>在一次联合查询内唯一，形如 depth:templateCode:triggerKeyType；标量键参数逐值执行时由服务端追加区分后缀。页面原样使用，不解析。</li>
     */
    private String nodeId;

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 执行的模板版本号。 */
    private int versionNo;

    /** 数据源编码。 */
    private String dataSourceCode;

    /** 行主体键类型编码。 */
    private String subjectKeyType;

    /** 行主体键类型的分组，即所在泳道。 */
    private String groupName;

    /** 泳道顺序：分组内 ACTIVE 键类型的最小 sortNo。 */
    private int laneRank;

    /** 行主体键类型的 sortNo，决定泳道内顺序。 */
    private int subjectSortNo;

    /** 触发键类型编码。 */
    private String triggerKeyType;

    /** 键值绑定的参数名。 */
    private String keyParameter;

    /**
     * 键参数是否为 LIST
     * <li>true：页面“加载更多”时 params[keyParameter] 传数组；false：传单个值（triggerValues 只有一项）。</li>
     */
    private boolean keyParameterList;

    /** 本次执行传入的键值（规范化后）。 */
    private List<String> triggerValues;

    /**
     * 执行状态
     * <li>SUCCEEDED；FAILED：errorCode 取 data-query 既有错误码或 INTERNAL_ERROR；
     * SKIPPED：errorCode 为 STEP_BUDGET_EXHAUSTED 或 STEP_EXECUTION_LIMIT，未执行。</li>
     */
    private String status;

    /** 失败或跳过的原因码；SUCCEEDED 为 null。 */
    private String errorCode;

    /** 安全中文消息；SUCCEEDED 为 null。 */
    private String errorMessage;

    /** 模板分页方式：NONE、OFFSET、CURSOR。 */
    private String pagingMode;

    /** 是否截断：行数达到 min(maxRowsPerTemplate, 模板上限) 或结果还有更多行。 */
    private boolean truncated;

    /**
     * 下一页游标
     * <li>只有 CURSOR 且结果还有更多行时返回，其余为 null；页面经单模板执行接口原样带回 cursor 继续取。</li>
     */
    private String nextCursor;

    /** 执行耗时毫秒；SKIPPED 为 0。 */
    private long elapsedMillis;

    /** 执行提示，如 LINK_COLUMN_MISSING:order_id；没有时为空列表。 */
    private List<String> warnings;

    /** 结果列，已去掉 HIDE 列；FAILED、SKIPPED 为空列表。 */
    private List<JointQueryNodeColumn> columns;

    /**
     * 数据行
     * <li>与单模板执行相同：每行与 columns 一一对应，整数、DECIMAL、日期时间等为字符串，NULL 为 null，屏蔽列按规则替换。</li>
     * <li>FAILED、SKIPPED 为空列表。</li>
     */
    private List<List<Object>> rows;
}
