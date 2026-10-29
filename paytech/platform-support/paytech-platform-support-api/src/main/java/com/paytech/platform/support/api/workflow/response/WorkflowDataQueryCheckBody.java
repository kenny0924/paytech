package com.paytech.platform.support.api.workflow.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** DATA_QUERY 动作的系统检查块：完成标准与最近一次检查。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDataQueryCheckBody {

    /** data-query 模板编码（版本快照）。 */
    private String queryCode;

    /** 完成标准文案键；可空。 */
    private String descriptionKey;

    /** 完成标准说明，文案键无译文时显示；可空。 */
    private String instruction;

    /**
     * 最近一次检查结果
     * <li>SATISFIED、UNSATISFIED、FAILED；从未检查为空。</li>
     */
    private String result;

    /**
     * 还差的原因码
     * <li>result 为 UNSATISFIED 时为查询返回的原因码（可为空列表）；其他为空列表。</li>
     */
    private List<String> missing;

    /**
     * 失败码
     * <li>result 为 FAILED 时为 TEMPLATE_UNAVAILABLE、PARAMETER_INVALID、QUERY_TIMEOUT、DATA_SOURCE_UNAVAILABLE、
     * RESULT_TOO_LARGE、RESULT_SHAPE_INVALID 或 QUERY_FAILED；其他为空。</li>
     */
    private String failureCode;

    /** 最近一次检查结果落库的 UTC 时间；从未检查为空。 */
    private Instant lastCheckedAt;

    /** 最近一次查询成功执行（满足或未满足）的 UTC 时间；从未成功为空。 */
    private Instant lastSucceededAt;

    /** 连续失败次数；查询成功执行后清零。 */
    private int consecutiveFailures;

    /** 首查尚未完成：动作待处理且从未检查。 */
    private boolean checking;
}
