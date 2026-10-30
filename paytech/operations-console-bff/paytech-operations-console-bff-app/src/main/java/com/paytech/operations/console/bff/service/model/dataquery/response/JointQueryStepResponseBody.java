package com.paytech.operations.console.bff.service.model.dataquery.response;

import java.util.List;

import com.paytech.operations.console.bff.service.model.dataquery.request.JointQueryKeyValues;

import lombok.Getter;
import lombok.Setter;

/** 联合查询一层的执行结果。 */
@Getter
@Setter
public class JointQueryStepResponseBody {

    /** 层号，与请求相同。 */
    private int depth;

    /** 本层执行的节点；本层没有参与模板时为空列表。 */
    private List<JointQueryNode> nodes;

    /** 本层结果中 LINK 列的非空值；没有时为空列表。 */
    private List<JointQueryLink> links;

    /**
     * 本层发现的键
     * <li>links 按键类型去重，每个键类型最多 maxValuesPerKey 个值（超出记 MAX_VALUES_PER_KEY）；
     * 页面扣除已访问的值后作为下一层 keys。没有时为空列表。</li>
     */
    private List<JointQueryKeyValues> discoveredKeys;

    /** 本层触发的截断；没有时为空列表。 */
    private List<JointQueryTruncation> truncations;

    /**
     * 因已加载而未执行的“按自身 ID 查”视图
     * <li>请求带 loadedSubjects 时产生（全部或部分值被剔除的模板各一项）；不计入执行数。没有时为空列表。</li>
     */
    private List<JointQueryAlreadyLoaded> alreadyLoaded;

    /** 本层总耗时毫秒。 */
    private long elapsedMillis;
}
