package com.paytech.billing.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.billing.api.ChannelCostPlanApi;
import com.paytech.billing.api.request.CreateChannelCostPlanRequestBody;
import com.paytech.billing.api.request.CreateChannelCostVersionRequestBody;
import com.paytech.billing.api.request.ReviewChannelCostVersionRequestBody;
import com.paytech.billing.api.request.SubmitChannelCostVersionRequestBody;
import com.paytech.billing.api.response.ChannelCostPlanResponseBody;
import com.paytech.billing.api.response.ChannelCostVersionResponseBody;
import com.paytech.billing.app.ext.rest.converter.ChannelCostPlanConverter;
import com.paytech.billing.domainservice.service.ChannelCostPlanService;
import com.paytech.billing.service.processor.ApproveChannelCostVersionProcessor;
import com.paytech.billing.service.processor.CreateChannelCostPlanProcessor;
import com.paytech.billing.service.processor.CreateChannelCostVersionProcessor;
import com.paytech.billing.service.processor.RejectChannelCostVersionProcessor;
import com.paytech.billing.service.processor.SubmitChannelCostVersionProcessor;

import lombok.AllArgsConstructor;

/** 渠道成本方案的 HTTP 协议适配；查询直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class ChannelCostPlanController implements ChannelCostPlanApi {
    private final ChannelCostPlanService channelCostPlanService;
    private final ChannelCostPlanConverter converter;
    private final CreateChannelCostPlanProcessor createChannelCostPlanProcessor;
    private final CreateChannelCostVersionProcessor createChannelCostVersionProcessor;
    private final RejectChannelCostVersionProcessor rejectChannelCostVersionProcessor;
    private final SubmitChannelCostVersionProcessor submitChannelCostVersionProcessor;
    private final ApproveChannelCostVersionProcessor approveChannelCostVersionProcessor;

    /** 创建方案并返回 201。 */
    @Override
    public ResponseEntity<ChannelCostPlanResponseBody> createChannelCostPlan(CreateChannelCostPlanRequestBody body) {
        ChannelCostPlanResponseBody response =
            converter.toResponseBody(createChannelCostPlanProcessor.process(converter.toRequest(body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 创建版本并返回 201。 */
    @Override
    public ResponseEntity<ChannelCostVersionResponseBody> createChannelCostVersion(String planCode,
        CreateChannelCostVersionRequestBody body) {
        ChannelCostVersionResponseBody response =
            converter.toResponseBody(createChannelCostVersionProcessor.process(converter.toRequest(planCode, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 提交版本待复核。 */
    @Override
    public ChannelCostVersionResponseBody submitChannelCostVersion(String planCode, String costVer,
        SubmitChannelCostVersionRequestBody body) {
        return converter.toResponseBody(
            submitChannelCostVersionProcessor.process(converter.toRequest(planCode, costVer, body)));
    }

    /** 批准版本。 */
    @Override
    public ChannelCostVersionResponseBody approveChannelCostVersion(String planCode, String costVer,
        ReviewChannelCostVersionRequestBody body) {
        return converter.toResponseBody(
            approveChannelCostVersionProcessor.process(converter.toRequest(planCode, costVer, body)));
    }

    /** 驳回版本。 */
    @Override
    public ChannelCostVersionResponseBody rejectChannelCostVersion(String planCode, String costVer,
        ReviewChannelCostVersionRequestBody body) {
        return converter.toResponseBody(
            rejectChannelCostVersionProcessor.process(converter.toRequest(planCode, costVer, body)));
    }

    /** 查询方案与版本摘要。 */
    @Override
    public ChannelCostPlanResponseBody getChannelCostPlan(String planCode) {
        return converter.toResponseBody(channelCostPlanService.getPlan(planCode));
    }

    /** 查询版本详情。 */
    @Override
    public ChannelCostVersionResponseBody getChannelCostVersion(String planCode, String costVer) {
        return converter.toResponseBody(channelCostPlanService.getVersion(planCode, Integer.parseInt(costVer)));
    }
}
