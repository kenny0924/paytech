package com.paytech.payment.execution.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.action.ActionResult;

/** 支付动作 SQL 映射 */
@Mapper
public interface PaymentActionMapper {
    /** 插入已构造的动作。 */
    int insertAction(PaymentAction action);
    /** 使用原动作匹配状态和版本，更新值来自执行结果。 */
    int saveResult(@Param("action") PaymentAction action, @Param("result") ActionResult result,
        @Param("now") Instant now);

    /** 按主键读取原动作。 */
    PaymentAction selectById(@Param("actionId") Long actionId);

    /** 按动作序号读取尝试的最后一个动作。 */
    PaymentAction selectLatestByAttempt(@Param("attemptId") long attemptId);
}
