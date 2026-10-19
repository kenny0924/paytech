package com.paytech.commercial.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.term.CommercialTermApi;
import com.paytech.commercial.api.term.request.CommercialTermVersionCommandRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermPlanRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermVersionRequestBody;
import com.paytech.commercial.api.term.request.ReplaceCommercialTermPricesRequestBody;
import com.paytech.commercial.api.term.request.ScheduleCommercialTermVersionRequestBody;
import com.paytech.commercial.api.term.response.CommercialTermPlanResponseBody;
import com.paytech.commercial.api.term.response.CommercialTermVersionResponseBody;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.service.processor.ActivateCommercialTermVersionProcessor;
import com.paytech.commercial.service.processor.ApproveCommercialTermVersionProcessor;
import com.paytech.commercial.service.processor.CreateCommercialTermPlanProcessor;
import com.paytech.commercial.service.processor.CreateCommercialTermVersionProcessor;
import com.paytech.commercial.service.processor.RejectCommercialTermVersionProcessor;
import com.paytech.commercial.service.processor.ReplaceCommercialTermPricesProcessor;
import com.paytech.commercial.service.processor.ScheduleCommercialTermVersionProcessor;
import com.paytech.commercial.service.processor.SubmitCommercialTermVersionProcessor;
import com.paytech.commercial.service.processor.response.CreateCommercialTermPlanResponse;

import lombok.AllArgsConstructor;

/** 商业条款写入的 HTTP 协议适配：每个写操作委托一个 Processor，版本详情直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class CommercialTermController implements CommercialTermApi {
    private final CommercialTermService commercialTermService;
    private final CreateCommercialTermPlanProcessor createPlanProcessor;
    private final CreateCommercialTermVersionProcessor createVersionProcessor;
    private final RejectCommercialTermVersionProcessor rejectProcessor;
    private final ReplaceCommercialTermPricesProcessor replacePricesProcessor;
    private final SubmitCommercialTermVersionProcessor submitProcessor;
    private final ApproveCommercialTermVersionProcessor approveProcessor;
    private final ActivateCommercialTermVersionProcessor activateProcessor;
    private final ScheduleCommercialTermVersionProcessor scheduleProcessor;

    /** 新建返回 201，同号同内容重放返回 200。 */
    @Override
    public ResponseEntity<CommercialTermPlanResponseBody> createPlan(String actorId,
        CreateCommercialTermPlanRequestBody body) {
        CreateCommercialTermPlanResponse response =
            createPlanProcessor.process(CommercialTermConverter.toRequest(actorId, body));
        return ResponseEntity.status(response.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(CommercialTermConverter.toPlanResponseBody(response.getView()));
    }

    /** 创建版本并返回 201。 */
    @Override
    public ResponseEntity<CommercialTermVersionResponseBody> createVersion(String actorId, String planId,
        CreateCommercialTermVersionRequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CommercialTermConverter.toVersionResponseBody(
            createVersionProcessor.process(CommercialTermConverter.toRequest(actorId, planId, body))));
    }

    /** 整体替换价格集合。 */
    @Override
    public CommercialTermVersionResponseBody replacePrices(String actorId,
        String versionId, ReplaceCommercialTermPricesRequestBody body) {
        return CommercialTermConverter.toVersionResponseBody(
            replacePricesProcessor.process(CommercialTermConverter.toRequest(actorId, versionId, body)));
    }

    /** 提交审批。 */
    @Override
    public CommercialTermVersionResponseBody submitVersion(String actorId,
        String versionId, CommercialTermVersionCommandRequestBody body) {
        return CommercialTermConverter.toVersionResponseBody(
            submitProcessor.process(CommercialTermConverter.toRequest(actorId, versionId, body)));
    }

    /** 批准。 */
    @Override
    public CommercialTermVersionResponseBody approveVersion(String actorId,
        String versionId, CommercialTermVersionCommandRequestBody body) {
        return CommercialTermConverter.toVersionResponseBody(
            approveProcessor.process(CommercialTermConverter.toRequest(actorId, versionId, body)));
    }

    /** 驳回。 */
    @Override
    public CommercialTermVersionResponseBody rejectVersion(String actorId,
        String versionId, CommercialTermVersionCommandRequestBody body) {
        return CommercialTermConverter.toVersionResponseBody(
            rejectProcessor.process(CommercialTermConverter.toRequest(actorId, versionId, body)));
    }

    /** 排期。 */
    @Override
    public CommercialTermVersionResponseBody scheduleVersion(String actorId,
        String versionId, ScheduleCommercialTermVersionRequestBody body) {
        return CommercialTermConverter.toVersionResponseBody(
            scheduleProcessor.process(CommercialTermConverter.toRequest(actorId, versionId, body)));
    }

    /** 手工激活（与定时作业同一用例）。 */
    @Override
    public CommercialTermVersionResponseBody activateVersion(String actorId,
        String versionId, CommercialTermVersionCommandRequestBody body) {
        return CommercialTermConverter.toVersionResponseBody(
            activateProcessor.process(CommercialTermConverter.toRequest(actorId, versionId, body)));
    }

    /** 版本详情。 */
    @Override
    public CommercialTermVersionResponseBody getVersion(String versionId) {
        return CommercialTermConverter.toVersionResponseBody(commercialTermService.getVersion(Long.parseLong(versionId)));
    }
}
