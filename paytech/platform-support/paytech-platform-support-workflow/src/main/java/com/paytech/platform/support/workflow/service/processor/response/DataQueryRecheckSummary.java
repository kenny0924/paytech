package com.paytech.platform.support.workflow.service.processor.response;

import lombok.Getter;

/**
 * 一轮定时重查的计数。
 * <li>checked 为执行并落库的检查数，其中 completed / unsatisfied / queryFailed 按结果分；skipped 为检查锁被占用
 * （已有检查在进行）；busy 为落库时实例锁或版本冲突；failed 为处理中的意外异常（导致 XXL 记失败）。</li>
 */
@Getter
public class DataQueryRecheckSummary {

    /** 执行并落库的检查数。 */
    private int checked;

    /** 其中已满足并完成的动作数。 */
    private int completed;

    /** 其中未满足的动作数。 */
    private int unsatisfied;

    /** 其中查询失败、超时或形状不符的动作数。 */
    private int queryFailed;

    /** 检查锁被占用而跳过的动作数。 */
    private int skipped;

    /** 落库冲突而丢弃结果的动作数。 */
    private int busy;

    /** 处理中出现意外异常的动作数。 */
    private int failed;

    /**
     * 计入一次检查结果。
     *
     * @param view 检查结果
     */
    public void add(DataQueryCheckView view) {
        if (view.isBusy()) {
            busy++;
            return;
        }
        if (!view.isRecorded()) {
            return;
        }
        checked++;
        switch (view.getResult()) {
            case SATISFIED -> completed++;
            case UNSATISFIED -> unsatisfied++;
            case FAILED -> queryFailed++;
        }
    }

    /** 计入一次检查锁冲突。 */
    public void skip() {
        skipped++;
    }

    /** 计入一次意外异常。 */
    public void fail() {
        failed++;
    }
}
