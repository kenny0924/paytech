package com.paytech.client.master.integration.impl;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.client.master.integration.ClientPaymentMethodCatalogClient;
import com.paytech.commercial.api.catalog.ProductCatalogApi;
import com.paytech.commercial.api.catalog.response.ProductCapabilityListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductCapabilitySummary;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * 经 commercial-api 读取产品能力目录中 ACTIVE 的支付方式（业务信息补采 S-05、S-06）。
 * <li>只按 status=ACTIVE 查询、在本地过滤 PAYMENT_METHOD；产品代码不限。</li>
 * <li>失败关闭：调用失败、响应缺失或任一支付方式行的代码不合规都按依赖不可用处理，不返回部分选项；只记录失败类别，不记录响应内容。</li>
 */
@AllArgsConstructor
public class ClientPaymentMethodCatalogClientImpl implements ClientPaymentMethodCatalogClient {
    /** commercial 可开通项种类：支付方式。 */
    static final String PAYMENT_METHOD = "PAYMENT_METHOD";
    /** commercial 可开通项状态：ACTIVE。 */
    static final String ACTIVE = "ACTIVE";
    private static final Logger LOG = LoggerFactory.getLogger(ClientPaymentMethodCatalogClientImpl.class);

    private final ProductCatalogApi api;

    /** 一次查询全部 ACTIVE 可开通项并取支付方式代码。 */
    @Override
    public List<String> activePaymentMethodCodes() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("commercial 调用不能位于数据库事务内");
        }
        ProductCapabilityListResponseBody response;
        try {
            response = api.listCapabilities(null, ACTIVE);
        } catch (RuntimeException failure) {
            LOG.warn("commercial 支付方式目录不可用: {}", failure.getClass().getSimpleName());
            throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE, "commercial 支付方式目录不可用", failure);
        }
        if (response == null || response.getCapabilities() == null) {
            LOG.warn("commercial 支付方式目录响应不合法：缺少可开通项列表");
            throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE, "commercial 支付方式目录不可用");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (ProductCapabilitySummary capability : response.getCapabilities()) {
            if (capability == null) {
                throw invalid();
            }
            if (!PAYMENT_METHOD.equals(capability.getCapabilityKind()) || !ACTIVE.equals(capability.getStatus())) {
                continue;
            }
            if (capability.getCapabilityCode() == null || !capability.getCapabilityCode().matches("[A-Z][A-Z0-9_]{0,63}")) {
                throw invalid();
            }
            codes.add(capability.getCapabilityCode());
        }
        return List.copyOf(codes);
    }

    /** 支付方式行不合规：失败关闭。 */
    private static ErrorException invalid() {
        LOG.warn("commercial 支付方式目录响应不合法：可开通项代码不合规");
        return new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE, "commercial 支付方式目录不可用");
    }
}
