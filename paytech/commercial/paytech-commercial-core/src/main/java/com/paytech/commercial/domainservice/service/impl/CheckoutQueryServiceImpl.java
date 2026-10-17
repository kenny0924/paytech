package com.paytech.commercial.domainservice.service.impl;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.OWNER_UNAVAILABLE;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.domain.CommercialCheckoutFacts;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domainservice.repository.*;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * 在一个 REPEATABLE READ 只读事务中读取全部商务事实（design/06 §2.2 第 2 步）；不持有数据库行锁。
 * <li>快照保护合同、参与方、条款版本、价格与合同根来自同一提交时点，混合时点会让签署与定价校验或复核比对失真。</li>
 */
@AllArgsConstructor
public class CheckoutQueryServiceImpl implements CheckoutQueryService {
    private final TransactionTemplate reads;

    private final AgreementRepository agreementRepository;
    private final AgreementPartyRepository agreementPartyRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final AgreementDocumentRefRepository agreementDocumentRefRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final PaymentMethodProfileRepository paymentMethodProfileRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final CommercialTermComponentRepository commercialTermComponentRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;
    private final CommercialTermComponentBandRepository commercialTermComponentBandRepository;

    /** 数据库故障保持不可用语义；业务冲突在内存判断阶段产生，不在这里转换。 */
    @Override
    public CommercialCheckoutFacts load(CommercialCheckoutScope scope) {
        try {
            return reads.execute(status -> new CommercialCheckoutFacts(agreementRepository.findForCheckout(scope),
                agreementOperatingEntityRepository.findForCheckout(scope), agreementPartyRepository.findForCheckout(scope),
                agreementDocumentRefRepository.findForCheckout(scope), commercialTermPlanRepository.findForCheckout(scope),
                commercialTermVersionRepository.findForCheckout(scope), commercialTermPriceRepository.findForCheckout(scope),
                commercialTermComponentRepository.findForCheckout(scope),
                commercialTermComponentBandRepository.findForCheckout(scope),
                paymentMethodProfileRepository.findForCheckout(scope), businessChangeRecordRepository.findCheckoutRoots(scope)));
        } catch (DataAccessException | TransactionException exception) {
            throw new ErrorException(OWNER_UNAVAILABLE, "commercial 数据读取不可用", exception);
        }
    }
}
