package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.body;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.request;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.write;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.cost.ChannelCostPlanApi;
import com.paytech.channel.connector.api.cost.request.SubmitChannelCostVersionRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ChannelCostClient;
import com.paytech.operations.console.bff.service.model.ChannelModels.CancelChannelCostVersionRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostPlanResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostPlanSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostVersionResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelCostPlanRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelCostVersionRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ReviewChannelCostVersionRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * channel-connector 渠道成本接口的 HTTP 转发：每个入口只调用一次 channel-connector，不重试
 * <li>页面请求严格转为 Owner 请求后，由本类以会话 Account ID 补入 makerId / checkerId / operatorId；页面不能自报操作人。</li>
 * <li>回执的方案代码与版本号必须与请求一致；方案详情与版本详情必须带集合。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class ChannelCostClientImpl implements ChannelCostClient {
    private final ChannelCostPlanApi planApi;

    /** 方案分页。 */
    @Override
    public PagingResponseBody<ChannelCostPlanSummaryResponseBody> listPlans(String channelId, String status, int page,
        int size) {
        return page(planApi.listChannelCostPlans(channelId, status, page, size), page, size,
            ChannelCostPlanSummaryResponseBody.class);
    }

    /** 方案详情；回执方案代码与请求一致并带版本集合。 */
    @Override
    public ChannelCostPlanResponseBody plan(String planCode) {
        var plan = convert(planApi.getChannelCostPlan(planCode), ChannelCostPlanResponseBody.class);
        requireSame(planCode, plan.getPlanCode());
        require(plan.getVersions() != null);
        return plan;
    }

    /** 版本详情；回执方案与版本号与请求一致并带范围集合。 */
    @Override
    public ChannelCostVersionResponseBody version(String planCode, String costVer) {
        return requireVersion(planCode, costVer,
            convert(planApi.getChannelCostVersion(planCode, costVer), ChannelCostVersionResponseBody.class));
    }

    /** 创建方案；回执方案代码与渠道与请求一致。 */
    @Override
    public ChannelCostPlanResponseBody createPlan(CreateChannelCostPlanRequestBody request) {
        var plan = write(request, () -> body(planApi.createChannelCostPlan(request(request,
            com.paytech.channel.connector.api.cost.request.CreateChannelCostPlanRequestBody.class))),
            ChannelCostPlanResponseBody.class);
        requireSame(request.getPlanCode(), plan.getPlanCode());
        requireSame(request.getChannelId(), plan.getChannelId());
        require(plan.getVersions() != null);
        return plan;
    }

    /** 创建版本，编制人取会话；回执方案代码一致。 */
    @Override
    public ChannelCostVersionResponseBody createVersion(OperatorContext operator, String planCode,
        CreateChannelCostVersionRequestBody request) {
        var owner = request(request, com.paytech.channel.connector.api.cost.request.CreateChannelCostVersionRequestBody.class);
        owner.setMakerId(operator.accountId());
        var version = write(request, () -> body(planApi.createChannelCostVersion(planCode, owner)),
            ChannelCostVersionResponseBody.class);
        requireSame(planCode, version.getPlanCode());
        require(version.getScopes() != null);
        return version;
    }

    /** 提交，提交人取会话。 */
    @Override
    public ChannelCostVersionResponseBody submitVersion(OperatorContext operator, String planCode, String costVer) {
        var owner = new SubmitChannelCostVersionRequestBody();
        owner.setMakerId(operator.accountId());
        return sameVersion(planCode, costVer, null, () -> planApi.submitChannelCostVersion(planCode, costVer, owner));
    }

    /** 批准，复核人取会话。 */
    @Override
    public ChannelCostVersionResponseBody approveVersion(OperatorContext operator, String planCode, String costVer,
        ReviewChannelCostVersionRequestBody request) {
        var owner = review(operator, request);
        return sameVersion(planCode, costVer, request, () -> planApi.approveChannelCostVersion(planCode, costVer, owner));
    }

    /** 驳回，复核人取会话。 */
    @Override
    public ChannelCostVersionResponseBody rejectVersion(OperatorContext operator, String planCode, String costVer,
        ReviewChannelCostVersionRequestBody request) {
        var owner = review(operator, request);
        return sameVersion(planCode, costVer, request, () -> planApi.rejectChannelCostVersion(planCode, costVer, owner));
    }

    /** 作废，作废人取会话。 */
    @Override
    public ChannelCostVersionResponseBody cancelVersion(OperatorContext operator, String planCode, String costVer,
        CancelChannelCostVersionRequestBody request) {
        var owner = request(request, com.paytech.channel.connector.api.cost.request.CancelChannelCostVersionRequestBody.class);
        owner.setOperatorId(operator.accountId());
        return sameVersion(planCode, costVer, request, () -> planApi.cancelChannelCostVersion(planCode, costVer, owner));
    }

    /** 复核请求转 Owner 合同并补入复核人。 */
    private static com.paytech.channel.connector.api.cost.request.ReviewChannelCostVersionRequestBody review(
        OperatorContext operator, ReviewChannelCostVersionRequestBody request) {
        var owner = request(request, com.paytech.channel.connector.api.cost.request.ReviewChannelCostVersionRequestBody.class);
        owner.setCheckerId(operator.accountId());
        return owner;
    }

    /** 版本命令回执；方案代码与版本号与路径一致。 */
    private static ChannelCostVersionResponseBody sameVersion(String planCode, String costVer, Object request,
        Supplier<Object> call) {
        return requireVersion(planCode, costVer, write(request, call, ChannelCostVersionResponseBody.class));
    }

    /** 版本回执的方案、版本号与范围集合核对。 */
    private static ChannelCostVersionResponseBody requireVersion(String planCode, String costVer,
        ChannelCostVersionResponseBody version) {
        requireSame(planCode, version.getPlanCode());
        requireSame(costVer, version.getCostVer());
        require(version.getScopes() != null);
        return version;
    }
}
