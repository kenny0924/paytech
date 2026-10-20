package com.paytech.channel.connector.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.cost.ChannelCostPlanApi;
import com.paytech.channel.connector.api.cost.request.CancelChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostPlanRequestBody;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.ReviewChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.SubmitChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostPlanResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostPlanSummaryResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostVersionResponseBody;
import com.paytech.channel.connector.domainservice.service.ChannelCostPlanService;
import com.paytech.channel.connector.ext.rest.converter.ChannelCostPlanConverter;
import com.paytech.channel.connector.service.processor.ApproveChannelCostVersionProcessor;
import com.paytech.channel.connector.service.processor.CancelChannelCostVersionProcessor;
import com.paytech.channel.connector.service.processor.CreateChannelCostPlanProcessor;
import com.paytech.channel.connector.service.processor.CreateChannelCostVersionProcessor;
import com.paytech.channel.connector.service.processor.RejectChannelCostVersionProcessor;
import com.paytech.channel.connector.service.processor.SubmitChannelCostVersionProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 渠道成本方案的 HTTP 协议适配：写入口委托 Processor，查询直接委托本地 Service。 */
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
    private final CancelChannelCostVersionProcessor cancelChannelCostVersionProcessor;

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

    /** 作废未到生效时间的已批准版本。 */
    @Override
    public ChannelCostVersionResponseBody cancelChannelCostVersion(String planCode, String costVer,
        CancelChannelCostVersionRequestBody body) {
        return converter.toResponseBody(
            cancelChannelCostVersionProcessor.process(converter.toRequest(planCode, costVer, body)));
    }

    /** 分页查询方案列表，直接委托本地 Service。 */
    @Override
    public PagingResponseBody<ChannelCostPlanSummaryResponseBody> listChannelCostPlans(String channelId, String status,
        Integer page, Integer size) {
        return channelCostPlanService.findPage(converter.optionalId(channelId), converter.planStatus(status),
            PagingRequestBody.of(page, size)).map(converter::toSummaryResponseBody);
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
