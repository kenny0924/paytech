package com.paytech.platform.support.workflow.domain.runtime;

import java.util.List;

import com.paytech.platform.support.workflow.core.enums.DataQueryCheckFailure;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult;

import tools.jackson.databind.node.ObjectNode;

/**
 * 一次 DATA_QUERY 检查的解读结果（查询已在事务外执行）。
 *
 * @param result 满足、未满足或失败
 * @param outputs 满足时按声明类型规整后的输出，只含声明的输出；其他结果为 null
 * @param missing 未满足时的原因码；其他结果为空列表
 * @param failure 失败码；非失败为 null
 */
public record DataQueryCheckOutcome(DataQueryCheckResult result, ObjectNode outputs, List<String> missing,
    DataQueryCheckFailure failure) {

    /** 复制列表，保持不可变。 */
    public DataQueryCheckOutcome {
        missing = missing == null ? List.of() : List.copyOf(missing);
    }

    /**
     * 已满足。
     *
     * @param outputs 规整后的输出
     * @return 结果
     */
    public static DataQueryCheckOutcome satisfied(ObjectNode outputs) {
        return new DataQueryCheckOutcome(DataQueryCheckResult.SATISFIED, outputs, List.of(), null);
    }

    /**
     * 查询成功但未满足。
     *
     * @param missing 原因码
     * @return 结果
     */
    public static DataQueryCheckOutcome unsatisfied(List<String> missing) {
        return new DataQueryCheckOutcome(DataQueryCheckResult.UNSATISFIED, null, missing, null);
    }

    /**
     * 检查失败。
     *
     * @param failure 失败码
     * @return 结果
     */
    public static DataQueryCheckOutcome failed(DataQueryCheckFailure failure) {
        return new DataQueryCheckOutcome(DataQueryCheckResult.FAILED, null, List.of(), failure);
    }
}
