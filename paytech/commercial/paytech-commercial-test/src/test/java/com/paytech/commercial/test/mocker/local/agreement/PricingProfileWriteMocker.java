package com.paytech.commercial.test.mocker.local.agreement;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.builder.PricingProfileCondBuilder;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

import lombok.AllArgsConstructor;

/**
 * 运营写入用例的定价画像前置数据：经正式 Repository 插入画像、条件行与 PAYMENT_METHOD_PROFILE BCR Root，不调用被测写入命令
 * <li>无可变状态；Root history_id 为画像 ID + 7。</li>
 */
@TestComponent
@AllArgsConstructor
public class PricingProfileWriteMocker {
    private final PricingProfileRepository pricingProfileRepository;
    private final PricingProfileCondRepository pricingProfileCondRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;

    /**
     * 插入画像、条件行与 Root
     *
     * @param builder 画像 Builder
     * @param rootVersion Root 当前版本
     * @param conditions 条件 Builder，所属画像取本画像
     * @return 已插入的画像
     */
    public PricingProfile mock(PricingProfileBuilder builder, long rootVersion, PricingProfileCondBuilder... conditions) {
        PricingProfile profile = builder.build();
        pricingProfileRepository.insert(profile);
        List<PricingProfileCond> rows = Arrays.stream(conditions)
            .map(condition -> condition.pricingProfileId(profile.getPricingProfileId()).build()).toList();
        pricingProfileCondRepository.insertAll(rows);
        BusinessChangeRecord root = new BusinessChangeRecord();
        root.setHistoryId(profile.getPricingProfileId() + 7);
        root.setRecordType(BusinessChangeRecordRecordType.PAYMENT_METHOD_PROFILE);
        root.setRecordId(Long.toString(profile.getPricingProfileId()));
        root.setAggregateRootType(BusinessChangeRecordAggregateRootType.PAYMENT_METHOD_PROFILE);
        root.setAggregateRootId(Long.toString(profile.getPricingProfileId()));
        root.setCurrentVersion(rootVersion);
        root.setCreateTime(CheckoutFixture.NOW);
        root.setUpdateTime(CheckoutFixture.NOW);
        businessChangeRecordRepository.insert(root);
        return profile;
    }
}
