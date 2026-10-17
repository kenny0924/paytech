package com.paytech.foundation.processor;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;

import jakarta.validation.Validator;

/**
 * 同步命令处理模板。
 *
 * <p>
 * HTTP 校验、异常响应和请求日志由现有 Web 基础设施负责；本模板统一执行应用请求的字段约束并固定处理顺序。
 *
 * @param <Request>
 *            应用请求
 * @param <Response>
 *            应用结果
 */
public abstract class AbstractProcessor<Request, Response> {

    private Validator validator;

    /**
     * 复用宿主的校验器，避免每个业务子类重复传递公共依赖。
     *
     * @param validator
     *            Spring 管理的校验器；非 Spring 创建的 Processor 须在使用前显式设置
     */
    @Autowired
    public final void setValidator(Validator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * 在调用方事务外校验并处理命令。
     *
     * @param request
     *            应用请求
     * @return 应用处理结果
     */
    public final Response process(Request request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("processor不能处于事务中调用");
        }
        validateBasic(request);
        validate(request);
        return processRequest(request);
    }

    /** 校验请求非空及默认分组的字段、嵌套对象约束，在业务校验、加锁及事务之前完成。 */
    protected void validateBasic(Request request) {
        if (request == null) {
            throw new InvalidParameterException("请求不能为空");
        }
        if (validator == null) {
            throw new ErrorException("Processor 未配置 Validator");
        }
        var failures = validator.validate(request);
        if (!failures.isEmpty()) {
            throw new InvalidParameterException("请求字段不符合约束：" + failures.iterator().next().getPropertyPath());
        }
    }

    /** 执行具体命令仅依赖输入的附加无副作用校验，不重复基础字段约束。 */
    protected void validate(Request request) {}

    /** 执行具体命令。 */
    protected abstract Response processRequest(Request request);
}
