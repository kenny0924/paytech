package com.paytech.risk.service.convert;

import java.util.function.Supplier;

import com.paytech.foundation.common.error.FailException;
import com.paytech.risk.decision.api.enums.RiskVariableErrorCode;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;

/** 变量领域失败到已登记业务 Code 的 HTTP 边界转换，不拥有业务编排。 */
public final class RiskVariableExceptionConverter {
    /** 无实例状态的协议异常适配。 */
    private RiskVariableExceptionConverter() { }

    /**
     * 执行 Owner 用例并转换变量业务失败
     *
     * @param operation Owner Processor 调用
     * @return Owner 处理结果
     *         <li>只转换有明确客户端处理语义的变量失败，未知数据库/网络错误继续向上传播。</li>
     */
    public static <T> T mapped(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RiskVariableException failure) {
            throw new FailException(RiskVariableErrorCode.valueOf(failure.reason().name()), failure.getMessage(), failure);
        }
    }
}
