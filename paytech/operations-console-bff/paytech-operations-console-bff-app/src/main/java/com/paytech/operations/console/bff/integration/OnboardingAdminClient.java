package com.paytech.operations.console.bff.integration;

import java.util.List;

import com.paytech.client.master.api.onboardingadmin.request.IssueOnboardingCodeRequestBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminCatalogItemResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientDetailResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientSummaryResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminJurisdictionResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminOnboardingSchemaResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.onboardingcode.response.IssuedOnboardingCodeResponseBody;
import com.paytech.operations.console.bff.service.model.onboardingcode.response.OnboardingCodeListResponseBody;
import com.paytech.operations.console.bff.service.model.onboardingcode.response.OnboardingCodeResponseBody;

/** client-master 入驻配置、Client 与入驻码管理（CM-10/11/12，`ClientOnboardingAdminApi`）；单次调用，不自动重试。 */
public interface OnboardingAdminClient {
    /** CM-10 辖区包。 */
    List<AdminJurisdictionResponseBody> jurisdictions(OperatorContext operator);

    /** CM-10 某辖区的采集 Schema。 */
    List<AdminOnboardingSchemaResponseBody> schemas(OperatorContext operator, String jurisdiction);

    /**
     * CM-10 目录项
     *
     * @param operator 当前操作人
     * @param catalogCode 目录代码
     * @param jurisdiction 可选辖区；给出时只保留全局项（null 或 *）与该辖区项（client-master 返回全部辖区）
     * @return 目录项
     */
    List<AdminCatalogItemResponseBody> catalogItems(OperatorContext operator, String catalogCode, String jurisdiction);

    /** CM-11 Client 分页。 */
    PagingResponseBody<AdminClientSummaryResponseBody> clients(OperatorContext operator, Integer page, Integer size, String status,
        String jurisdiction, String responsiblePartnerId);

    /** CM-11 Client 详情。 */
    AdminClientDetailResponseBody client(OperatorContext operator, String clientId);

    /**
     * CM-12 Partner 的入驻码（掩码）
     *
     * @param operator 当前操作人
     * @param localPartnerId Local Partner ID
     * @param status 可选状态，BFF 过滤（client-master 不支持）
     * @return 入驻码
     */
    OnboardingCodeListResponseBody onboardingCodes(OperatorContext operator, String localPartnerId, String status);

    /** CM-12 签发入驻码；唯一一次返回明文码。 */
    IssuedOnboardingCodeResponseBody issueOnboardingCode(OperatorContext operator, String localPartnerId, IssueOnboardingCodeRequestBody request);

    /** CM-12 作废入驻码（client-master 不收原因）。 */
    OnboardingCodeResponseBody revokeOnboardingCode(OperatorContext operator, String localPartnerId, String codeId);
}
