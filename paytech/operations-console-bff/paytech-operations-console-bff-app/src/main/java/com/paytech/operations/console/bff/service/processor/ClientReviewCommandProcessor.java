package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.client.master.api.enums.EntityAdminErrorCode;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.service.processor.request.ClientReviewCommandRequest;

import lombok.AllArgsConstructor;

/**
 * 案件工作台写命令（O05、O07—O09、CP-5）：带代操作 PayFac 时先向 client-master 核对，再单次写 compliance（S-05）
 * <li>PayFac 必须存在、ACTIVE 且具有 OE_PROVIDER 角色；否则 422 ON_BEHALF_PARTNER_INVALID，不调用 compliance。</li>
 * <li>compliance 只记录 operated_by=PLATFORM 与 on_behalf_of_partner_id；决定人仍是当前运营账号，四眼与门禁以 compliance 为准。</li>
 * <li>核对与写入之间 Partner 状态仍可能变化，属可接受窗口；compliance 的 409 / 422 原样返回，不重试。</li>
 */
@Component
@AllArgsConstructor
public class ClientReviewCommandProcessor extends AbstractProcessor<ClientReviewCommandRequest, ClientReviewCommandResponseBody> {
    /** Partner 生效状态。 */
    static final String ACTIVE = "ACTIVE";

    private final EntityAdminClient entityAdmin;

    @Override
    protected ClientReviewCommandResponseBody processRequest(ClientReviewCommandRequest request) {
        if (request.getOnBehalfOfPartnerId() != null) {
            requireEligiblePartner(request);
        }
        return request.getCommand().get();
    }

    /** 代操作 PayFac 必须是生效的 OE 提供方；Partner 不存在同样按 422 拒绝。 */
    void requireEligiblePartner(ClientReviewCommandRequest request) {
        String partnerId = request.getOnBehalfOfPartnerId();
        try {
            var partner = entityAdmin.localPartner(request.getOperator(), partnerId);
            if (ACTIVE.equals(partner.getStatus()) && partner.getRoles() != null
                && partner.getRoles().contains(PartnerRoleCode.OE_PROVIDER.getCode())) {
                return;
            }
            throw invalid(partnerId, "状态: " + partner.getStatus() + ", 角色: " + partner.getRoles());
        } catch (FailException failure) {
            if (!EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND.getCode().equals(failure.getCode())) {
                throw failure;
            }
            throw invalid(partnerId, "Partner 不存在");
        }
    }

    /** 代操作 PayFac 不可用。 */
    static FailException invalid(String partnerId, String detail) {
        return new FailException(OperationsBffErrorCode.ON_BEHALF_PARTNER_INVALID, "代操作的 PayFac 不可用: " + partnerId + ", " + detail);
    }
}
