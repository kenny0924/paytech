package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CancelChannelCostVersionRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostPlanResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostPlanSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostVersionResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelCostPlanRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelCostVersionRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ReviewChannelCostVersionRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * channel-connector 渠道成本方案与版本的本地端口；Maker-Checker、摘要核对、生效切换与作废规则归 channel-connector
 * <li>成本合同的操作人在请求体：创建与提交填 makerId，批准与驳回填 checkerId，作废填 operatorId，均取会话 Account ID；
 * 页面模型不含这些字段。</li>
 * <li>每个方法只调用一次 channel-connector，不重试。</li>
 *
 * @author Claude
 */
public interface ChannelCostClient {

    /**
     * 方案分页
     *
     * @param channelId 可选渠道 ID
     * @param status 可选方案状态
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 方案列表项与总数
     */
    PagingResponseBody<ChannelCostPlanSummaryResponseBody> listPlans(String channelId, String status, int page, int size);

    /**
     * 方案详情与全部版本摘要
     *
     * @param planCode 方案代码
     * @return 方案
     */
    ChannelCostPlanResponseBody plan(String planCode);

    /**
     * 版本详情，含范围与组成
     *
     * @param planCode 方案代码
     * @param costVer 版本号
     * @return 版本
     */
    ChannelCostVersionResponseBody version(String planCode, String costVer);

    /**
     * 为渠道创建成本方案；Owner 合同没有操作人
     *
     * @param request 渠道与方案代码
     * @return 方案
     */
    ChannelCostPlanResponseBody createPlan(CreateChannelCostPlanRequestBody request);

    /**
     * 创建 DRAFT 版本，编制人为会话操作者
     *
     * @param operator 操作人
     * @param planCode 方案代码
     * @param request 生效时间与完整范围
     * @return 版本
     */
    ChannelCostVersionResponseBody createVersion(OperatorContext operator, String planCode,
        CreateChannelCostVersionRequestBody request);

    /**
     * 提交待复核，提交人为会话操作者（须为编制人）
     *
     * @param operator 操作人
     * @param planCode 方案代码
     * @param costVer 版本号
     * @return 版本
     */
    ChannelCostVersionResponseBody submitVersion(OperatorContext operator, String planCode, String costVer);

    /**
     * 批准，复核人为会话操作者（须不同于编制人）
     *
     * @param operator 操作人
     * @param planCode 方案代码
     * @param costVer 版本号
     * @param request 确认的内容摘要
     * @return 版本
     */
    ChannelCostVersionResponseBody approveVersion(OperatorContext operator, String planCode, String costVer,
        ReviewChannelCostVersionRequestBody request);

    /**
     * 驳回，复核人为会话操作者（须不同于编制人）
     *
     * @param operator 操作人
     * @param planCode 方案代码
     * @param costVer 版本号
     * @param request 确认的内容摘要
     * @return 版本
     */
    ChannelCostVersionResponseBody rejectVersion(OperatorContext operator, String planCode, String costVer,
        ReviewChannelCostVersionRequestBody request);

    /**
     * 作废未到生效时间的已批准版本，作废人为会话操作者
     *
     * @param operator 操作人
     * @param planCode 方案代码
     * @param costVer 版本号
     * @param request 作废原因
     * @return 版本
     */
    ChannelCostVersionResponseBody cancelVersion(OperatorContext operator, String planCode, String costVer,
        CancelChannelCostVersionRequestBody request);
}
