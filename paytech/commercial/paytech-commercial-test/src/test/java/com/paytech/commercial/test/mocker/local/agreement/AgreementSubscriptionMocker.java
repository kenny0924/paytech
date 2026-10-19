package com.paytech.commercial.test.mocker.local.agreement;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.api.enums.CapabilityLimitCode;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementProductRepository;
import com.paytech.commercial.test.builder.AgreementCapabilityBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityLimitBuilder;
import com.paytech.commercial.test.builder.AgreementProductBuilder;

import lombok.AllArgsConstructor;

/**
 * 合同订阅、开通项与约束的前置数据：经正式 Repository 插入，不调用被测写入命令。
 * <li>无可变状态；插入的行由 CommercialDataCleaner 在每例结束后统一清理。合同本身由 Checkout 夹具或其他用例准备。</li>
 */
@TestComponent
@AllArgsConstructor
public class AgreementSubscriptionMocker {
    private final AgreementProductRepository agreementProductRepository;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final AgreementCapabilityLimitRepository agreementCapabilityLimitRepository;

    /**
     * 插入一行合同订阅。
     *
     * @param builder 订阅 Builder
     * @return 已插入的订阅
     */
    public AgreementProduct mockProduct(AgreementProductBuilder builder) {
        AgreementProduct product = builder.build();
        agreementProductRepository.insert(product);
        return product;
    }

    /**
     * 插入一个开通项，返回可继续追加约束的 Mocker。
     *
     * @param builder 开通项 Builder
     * @return 持有该开通项的 Mocker
     */
    public Mocker mockCapability(AgreementCapabilityBuilder builder) {
        AgreementCapability capability = builder.build();
        agreementCapabilityRepository.insert(capability);
        return new Mocker(capability);
    }

    /** 已插入开通项的约束追加器；约束 ID 为开通项 ID × 100 + 序号，确定且不与其他开通项冲突。 */
    public class Mocker {
        private final AgreementCapability capability;
        private final List<AgreementCapabilityLimit> limits = new ArrayList<>();

        private Mocker(AgreementCapability capability) {
            this.capability = capability;
        }

        /**
         * 追加一条约束。
         *
         * @param limitCode 约束类别
         * @param limitValue 规范化值
         * @return 本 Mocker
         */
        public Mocker limit(CapabilityLimitCode limitCode, String limitValue) {
            AgreementCapabilityLimit limit = AgreementCapabilityLimitBuilder.template()
                .agreementCapabilityLimitId(capability.getAgreementCapabilityId() * 100 + limits.size() + 1)
                .agreementCapabilityId(capability.getAgreementCapabilityId())
                .limitCode(limitCode)
                .limitValue(limitValue)
                .build();
            agreementCapabilityLimitRepository.insertAll(List.of(limit));
            limits.add(limit);
            return this;
        }

        /** 追加一组 CURRENCY 约束。 */
        public Mocker currencies(String... currencies) {
            for (String currency : currencies) {
                limit(CapabilityLimitCode.CURRENCY, currency);
            }
            return this;
        }

        /** 已插入的开通项。 */
        public AgreementCapability capability() {
            return capability;
        }

        /** 已插入的约束，按追加顺序。 */
        public List<AgreementCapabilityLimit> limits() {
            return List.copyOf(limits);
        }
    }
}
