package com.paytech.commercial.test.mocker.local.term;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.builder.PricingProfileCondBuilder;
import com.paytech.commercial.test.dal.CommercialTestMapper;

import lombok.AllArgsConstructor;

/**
 * 条款与价格命中用例的前置数据：定价画像（含条件）与已生效方案的 BCR 审计身份；经正式 Repository / TestMapper 插入，不调用被测写入命令
 * <li>无可变状态；插入的行由 CommercialDataCleaner 在每例结束后统一清理。</li>
 */
@TestComponent
@AllArgsConstructor
public class TermPricingMocker {
    /** BCR 审计身份 ID 相对业务 ID 的偏移，避免与业务行 ID 相同。 */
    static final long HISTORY_OFFSET = 500;

    private final CommercialTestMapper testMapper;
    private final PricingProfileRepository pricingProfileRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final PricingProfileCondRepository pricingProfileCondRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final CommercialTermComponentRepository commercialTermComponentRepository;

    /**
     * 插入一个画像及其条件；条件的画像 ID 取画像的 ID，条件 ID 为画像 ID × 100 + 序号。
     *
     * @param profile 画像 Builder
     * @param conditions 条件 Builder（维度与值）
     * @return 已插入的画像
     */
    public PricingProfile mockProfile(PricingProfileBuilder profile, PricingProfileCondBuilder... conditions) {
        PricingProfile row = profile.build();
        pricingProfileRepository.insert(row);
        List<PricingProfileCond> rows = new ArrayList<>();
        for (PricingProfileCondBuilder condition : conditions) {
            rows.add(condition.profileConditionId(row.getPricingProfileId() * 100 + rows.size() + 1)
                .pricingProfileId(row.getPricingProfileId()).build());
        }
        pricingProfileCondRepository.insertAll(rows);
        return row;
    }

    /**
     * 为经数据图插入的方案与版本补 BCR 审计身份：方案根（当前修订 rootVersion）与各版本 Child。
     *
     * @param planId 方案 ID
     * @param rootVersion 根当前修订
     * @param versionIds 版本 ID
     */
    public void mockAudit(long planId, long rootVersion, long... versionIds) {
        testMapper.insertBusinessChangeRoot(planId + HISTORY_OFFSET, "COMMERCIAL_TERM_PLAN", planId, rootVersion);
        for (long versionId : versionIds) {
            testMapper.insertBusinessChangeChild(versionId + HISTORY_OFFSET, "COMMERCIAL_TERM_VERSION", versionId,
                "COMMERCIAL_TERM_PLAN", planId, planId + HISTORY_OFFSET, rootVersion);
        }
    }

    /**
     * 插入方案及其版本，并补 BCR 根（当前修订 rootVersion）与各版本 Child。
     *
     * @param plan 方案 Builder
     * @param rootVersion 根当前修订
     * @param versions 版本 Builder（方案 ID 取方案）
     * @return 已插入的版本，按参数顺序
     */
    public List<CommercialTermVersion> mockPlan(CommercialTermPlanBuilder plan, long rootVersion,
        CommercialTermVersionBuilder... versions) {
        CommercialTermPlan planRow = plan.build();
        commercialTermPlanRepository.insert(planRow);
        List<CommercialTermVersion> rows = new ArrayList<>();
        for (CommercialTermVersionBuilder version : versions) {
            CommercialTermVersion row = version.termPlanId(planRow.getTermPlanId()).build();
            commercialTermVersionRepository.insert(row);
            rows.add(row);
        }
        mockAudit(planRow.getTermPlanId(), rootVersion,
            rows.stream().mapToLong(CommercialTermVersion::getTermVersionId).toArray());
        return rows;
    }

    /**
     * 插入一条价格及其组件；组件的价格 ID 取价格的 ID。
     *
     * @param price 价格 Builder
     * @param components 组件 Builder
     * @return 已插入的价格
     */
    public CommercialTermPrice mockPrice(CommercialTermPriceBuilder price, CommercialTermComponentBuilder... components) {
        CommercialTermPrice row = price.build();
        commercialTermPriceRepository.insertAll(List.of(row));
        List<CommercialTermComponent> rows = new ArrayList<>();
        for (CommercialTermComponentBuilder component : components) {
            rows.add(component.termPriceId(row.getTermPriceId()).build());
        }
        commercialTermComponentRepository.insertAll(rows);
        return row;
    }
}
