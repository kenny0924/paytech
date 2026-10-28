package com.paytech.operations.console.bff.integration;

import java.util.List;

import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AddAgreementCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementCapabilityResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.SuspendAgreementCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.ResumeAgreementCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.CapabilityPrerequisiteCheckResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementDetail;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementTermVersion;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementTransitionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.CreateAgreementRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.RecordAgreementAcceptanceRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.ReplaceAgreementProductsRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.TerminateAgreementRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.UpdateAgreementCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * commercial 合同、签署、订阅、开通项与费率卡的本地端口；业务校验、错误与事实归 commercial
 * <li>命令以会话 Account ID 作为 X-Gateway-Actor-Subject 转发；查询不携带操作人。</li>
 *
 * @author Claude
 */
public interface CommercialAgreementClient {

    /**
     * 合同详情
     *
     * @param agreementId 合同 ID
     * @return 合同详情；回执合同 ID 与请求一致
     */
    AgreementDetail detail(String agreementId);

    /**
     * 某 Client 的某份 ACTIVE 合同在当前时点的当前与已排期 Client 条款版本（费率卡投影，不含价格）
     *
     * @param clientId Client ID
     * @param agreementId 合同 ID
     * @return 版本列表；费率卡不含该合同时为空列表
     */
    List<AgreementTermVersion> termVersions(String clientId, String agreementId);

    /**
     * 创建 DRAFT 合同
     *
     * @param operator 操作人
     * @param request 页面请求
     * @return 写入结果
     */
    AgreementResponseBody create(OperatorContext operator, CreateAgreementRequestBody request);

    /**
     * 提交签署
     *
     * @param operator 操作人
     * @param agreementId 合同 ID
     * @param request 版本条件
     * @return 写入结果
     */
    AgreementResponseBody submitForSignature(OperatorContext operator, String agreementId,
        AgreementTransitionRequestBody request);

    /**
     * 登记一个 Party 的签署结果
     *
     * @param operator 操作人
     * @param agreementId 合同 ID
     * @param partyId Party ID
     * @param request 签署结果
     * @return 写入结果
     */
    AgreementResponseBody recordAcceptance(OperatorContext operator, String agreementId, String partyId,
        RecordAgreementAcceptanceRequestBody request);

    /**
     * 激活已签署合同
     *
     * @param operator 操作人
     * @param agreementId 合同 ID
     * @param request 版本条件
     * @return 写入结果
     */
    AgreementResponseBody activate(OperatorContext operator, String agreementId, AgreementTransitionRequestBody request);

    /**
     * 终止 ACTIVE 合同
     *
     * @param operator 操作人
     * @param agreementId 合同 ID
     * @param request 终止内容与版本条件
     * @return 写入结果
     */
    AgreementResponseBody terminate(OperatorContext operator, String agreementId, TerminateAgreementRequestBody request);

    /**
     * 整体替换订阅产品
     *
     * @param operator 操作人
     * @param agreementId 合同 ID
     * @param request 产品集合与版本条件
     * @return 写入结果
     */
    AgreementResponseBody replaceProducts(OperatorContext operator, String agreementId,
        ReplaceAgreementProductsRequestBody request);

    /**
     * 新增开通项
     *
     * @param operator 操作人
     * @param agreementId 合同 ID
     * @param request 开通项内容与版本条件
     * @return 开通项写入结果
     */
    AgreementCapabilityResponseBody addCapability(OperatorContext operator, String agreementId,
        AddAgreementCapabilityRequestBody request);

    /**
     * 整体改写开通项
     *
     * @param operator 操作人
     * @param agreementId 合同 ID
     * @param agreementCapabilityId 开通项 ID
     * @param request 开通项内容与版本条件
     * @return 开通项写入结果
     */
    AgreementCapabilityResponseBody updateCapability(OperatorContext operator, String agreementId,
        String agreementCapabilityId, UpdateAgreementCapabilityRequestBody request);

    /**
     * 运营手动暂停开通项
     *
     * @param operator 操作人
     * @param agreementId 合同 ID
     * @param agreementCapabilityId 开通项 ID
     * @param request 暂停原因与版本条件
     * @return 开通项写入结果
     */
    AgreementCapabilityResponseBody suspendCapability(OperatorContext operator, String agreementId,
        String agreementCapabilityId, SuspendAgreementCapabilityRequestBody request);

    /**
     * 运营恢复手动暂停的开通项（回到等待前置）
     *
     * @param operator 操作人
     * @param agreementId 合同 ID
     * @param agreementCapabilityId 开通项 ID
     * @param request 版本条件
     * @return 开通项写入结果
     */
    AgreementCapabilityResponseBody resumeCapability(OperatorContext operator, String agreementId,
        String agreementCapabilityId, ResumeAgreementCapabilityRequestBody request);

    /**
     * 立即核对合同全部开通项的前置，必要时由 commercial 迁移执行状态；不携带操作人
     *
     * @param agreementId 合同 ID
     * @return 核对结果；回执合同 ID 与请求一致
     */
    CapabilityPrerequisiteCheckResponseBody checkPrerequisites(String agreementId);
}
