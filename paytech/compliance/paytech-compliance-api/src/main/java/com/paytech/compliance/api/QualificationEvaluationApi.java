package com.paytech.compliance.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.compliance.api.request.QualificationEvaluationRequestBody;
import com.paytech.compliance.api.response.QualificationEvaluationResponseBody;

import jakarta.validation.Valid;

/**
 * 角色资格判定的私网合同；只允许 commercial-service 在 Checkout 解析时调用，不注册到公共 Gateway
 * <li>X-Caller-Service 只防止调用范围误用，实际身份由部署网络保证；其他调用方与资格不存在使用同一 404 拒绝合同。</li>
 */
@HttpExchange("/compliance/v1/qualifications")
public interface QualificationEvaluationApi {

    /**
     * 批量判定主体在当前时间是否具备交易所需的角色资格
     *
     * @param callerService
     *            私网调用方，固定为 commercial-service
     * @param request
     *            1—50 个主体；每种角色要求的资格类型由 compliance 决定
     * @return 每个主体一条结论
     *         <li>只读；有效期按判定时间实时判定，资格合格的 Client 再检查生效中的结算冻结。</li>
     *         <li>调用方不是 commercial-service 时 404 QUALIFICATION_NOT_FOUND；角色标识格式与角色不符 422 QUALIFICATION_INVALID；
     *         主体为空或超过 50 个 422 VALIDATION_ERROR。</li>
     */
    @PostExchange("/evaluations")
    QualificationEvaluationResponseBody evaluate(
        @RequestHeader(value = "X-Caller-Service", required = false) String callerService,
        @Valid @RequestBody QualificationEvaluationRequestBody request);
}
