package com.paytech.risk.api.decision.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 启动影子运行的请求。 */
public class StartShadowRunRequestBody {

    /**
     * 目标样本数
     * <li>1 至 2000；回放当前生效发布最近的成功正式评估。</li>
     */
    @Min(1)
    @Max(2000)
    private int sampleTarget;

    /** 读取目标样本数。 */
    public int getSampleTarget() { return sampleTarget; }

    /** 设置目标样本数。 */
    public void setSampleTarget(int sampleTarget) { this.sampleTarget = sampleTarget; }
}
