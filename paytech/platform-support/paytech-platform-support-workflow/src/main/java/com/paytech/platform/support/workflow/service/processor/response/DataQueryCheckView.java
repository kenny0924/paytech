package com.paytech.platform.support.workflow.service.processor.response;

import com.paytech.platform.support.workflow.core.enums.DataQueryCheckFailure;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次 DATA_QUERY 检查的内部结果，供定时重查计数、补偿任务结果与立即检查响应使用。 */
@Getter
@AllArgsConstructor
public class DataQueryCheckView {

    /** 动作运行 ID。 */
    private final long actionRunId;

    /** 是否执行了查询；动作已完成、已取消或实例已结束时为 false。 */
    private final boolean executed;

    /** 是否已落库；查询后动作已变化或实例锁冲突时为 false（结果丢弃）。 */
    private final boolean recorded;

    /** 落库时实例锁或版本冲突，结果丢弃，待下一轮重查。 */
    private final boolean busy;

    /** 本次检查结果；未执行为 null。 */
    private final DataQueryCheckResult result;

    /** 失败码；非失败为 null。 */
    private final DataQueryCheckFailure failure;

    /**
     * 未执行查询（动作不再可检查）。
     *
     * @param actionRunId 动作运行 ID
     * @return 结果
     */
    public static DataQueryCheckView notExecuted(long actionRunId) {
        return new DataQueryCheckView(actionRunId, false, false, false, null, null);
    }
}
