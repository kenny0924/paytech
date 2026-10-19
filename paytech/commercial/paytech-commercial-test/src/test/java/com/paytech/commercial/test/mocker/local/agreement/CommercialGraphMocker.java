package com.paytech.commercial.test.mocker.local.agreement;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBandBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;

import lombok.AllArgsConstructor;

/**
 * 合同 → 方案 → 版本 → 价格 → 组件 → 档位的前置数据图：经正式 Repository 自上而下插入，供 B / C / D 线用例使用。
 * <li>各层 Builder 的 template() 已按默认 ID 相互关联；用例覆盖 Builder 字段即可改变某一层，父 ID 由本 Mocker 按已插入的父行补齐。</li>
 * <li>无可变状态；插入的行由 CommercialDataCleaner 在每例结束后统一清理。参与方、文件引用与订阅 / 开通项按需另行插入。</li>
 */
@TestComponent
@AllArgsConstructor
public class CommercialGraphMocker {
    private final AgreementRepository agreementRepository;
    private final PricingProfileRepository pricingProfileRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final CommercialTermComponentRepository commercialTermComponentRepository;
    private final CommercialTermComponentBandRepository commercialTermComponentBandRepository;

    /**
     * 以全部默认值插入完整图：DRAFT CLIENT 合同、DRAFT 银行卡画像、CLIENT_PRICING 方案、第 1 版、一条 USD 价格与一个百分比组件，无档位。
     *
     * @return 已插入的图
     */
    public Graph mockDefaultGraph() {
        return mockAgreement(AgreementBuilder.template()).profile(PricingProfileBuilder.template())
            .plan(CommercialTermPlanBuilder.template()).version(CommercialTermVersionBuilder.template())
            .price(CommercialTermPriceBuilder.template()).component(CommercialTermComponentBuilder.template());
    }

    /**
     * 插入合同，返回可继续向下追加的图。
     *
     * @param builder 合同 Builder
     * @return 持有该合同的图
     */
    public Graph mockAgreement(AgreementBuilder builder) {
        Agreement agreement = builder.build();
        agreementRepository.insert(agreement);
        return new Graph(agreement);
    }

    /** 已插入的数据图；每个追加方法挂到最近一次插入的父行下。 */
    public class Graph {
        private final Agreement agreement;
        private final List<PricingProfile> profiles = new ArrayList<>();
        private final List<CommercialTermPlan> plans = new ArrayList<>();
        private final List<CommercialTermVersion> versions = new ArrayList<>();
        private final List<CommercialTermPrice> prices = new ArrayList<>();
        private final List<CommercialTermComponent> components = new ArrayList<>();
        private final List<CommercialTermComponentBand> bands = new ArrayList<>();

        private Graph(Agreement agreement) {
            this.agreement = agreement;
        }

        /** 插入一个定价画像（全局，不挂父行）；价格默认引用 PricingProfileBuilder 的代码。 */
        public Graph profile(PricingProfileBuilder builder) {
            PricingProfile profile = builder.build();
            pricingProfileRepository.insert(profile);
            profiles.add(profile);
            return this;
        }

        /** 插入一个挂在本合同下的方案；CLIENT_PRICING 的合同 ID 取本合同。 */
        public Graph plan(CommercialTermPlanBuilder builder) {
            CommercialTermPlan plan = builder.build();
            if (plan.getAgreementId() != null) {
                plan.setAgreementId(agreement.getAgreementId());
            }
            commercialTermPlanRepository.insert(plan);
            plans.add(plan);
            return this;
        }

        /** 插入一个挂在最近方案下的版本，合同 ID 取本合同。 */
        public Graph version(CommercialTermVersionBuilder builder) {
            CommercialTermVersion version = builder.build();
            version.setTermPlanId(last(plans, "方案").getTermPlanId());
            version.setAgreementId(agreement.getAgreementId());
            commercialTermVersionRepository.insert(version);
            versions.add(version);
            return this;
        }

        /** 插入一个挂在最近版本下的价格。 */
        public Graph price(CommercialTermPriceBuilder builder) {
            CommercialTermPrice price = builder.build();
            price.setTermVersionId(last(versions, "版本").getTermVersionId());
            commercialTermPriceRepository.insertAll(List.of(price));
            prices.add(price);
            return this;
        }

        /** 插入一个挂在最近价格下的组件。 */
        public Graph component(CommercialTermComponentBuilder builder) {
            CommercialTermComponent component = builder.build();
            component.setTermPriceId(last(prices, "价格").getTermPriceId());
            commercialTermComponentRepository.insertAll(List.of(component));
            components.add(component);
            return this;
        }

        /** 插入一个挂在最近组件下的档位。 */
        public Graph band(CommercialTermComponentBandBuilder builder) {
            CommercialTermComponentBand band = builder.build();
            band.setTermComponentId(last(components, "组件").getTermComponentId());
            commercialTermComponentBandRepository.insertAll(List.of(band));
            bands.add(band);
            return this;
        }

        /** 已插入的合同。 */
        public Agreement agreement() {
            return agreement;
        }

        /** 已插入的画像，按插入顺序。 */
        public List<PricingProfile> profiles() {
            return List.copyOf(profiles);
        }

        /** 已插入的方案，按插入顺序。 */
        public List<CommercialTermPlan> plans() {
            return List.copyOf(plans);
        }

        /** 已插入的版本，按插入顺序。 */
        public List<CommercialTermVersion> versions() {
            return List.copyOf(versions);
        }

        /** 已插入的价格，按插入顺序。 */
        public List<CommercialTermPrice> prices() {
            return List.copyOf(prices);
        }

        /** 已插入的组件，按插入顺序。 */
        public List<CommercialTermComponent> components() {
            return List.copyOf(components);
        }

        /** 已插入的档位，按插入顺序。 */
        public List<CommercialTermComponentBand> bands() {
            return List.copyOf(bands);
        }

        private static <T> T last(List<T> rows, String parent) {
            if (rows.isEmpty()) {
                throw new AssertionError("数据图尚未插入父级" + parent + "，不能追加子行");
            }
            return rows.get(rows.size() - 1);
        }
    }
}
