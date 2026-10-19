package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domain.PricingProfileContext;
import com.paytech.commercial.domain.PricingProfileView;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.service.processor.request.CreatePricingProfileRequest.Condition;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 定价画像写入 Processor 的公共查询、条件行构造与响应
 * <li>画像 BCR Root 的 record_type 沿用 PAYMENT_METHOD_PROFILE（表改名不改审计类型）。</li>
 *
 * @param <Q> 内部请求
 * @param <R> 内部响应
 */
public abstract class AbstractPricingProfileWriteProcessor<Q, R> extends AbstractCommercialWriteProcessor<Q, R> {
    protected final IdGenerator idGenerator;
    protected final PricingProfileRepository pricingProfileRepository;
    protected final PricingProfileCondRepository pricingProfileCondRepository;

    /**
     * 注入公共依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param pricingProfileRepository 画像仓储
     * @param pricingProfileCondRepository 条件仓储
     * @param businessChangeRecordRepository BCR 仓储
     */
    protected AbstractPricingProfileWriteProcessor(Clock clock, IdGenerator idGenerator,
        PricingProfileRepository pricingProfileRepository, PricingProfileCondRepository pricingProfileCondRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        super(clock, businessChangeRecordRepository);
        this.idGenerator = idGenerator;
        this.pricingProfileRepository = pricingProfileRepository;
        this.pricingProfileCondRepository = pricingProfileCondRepository;
    }

    /** 画像必须存在。 */
    PricingProfile getProfile(long pricingProfileId) {
        PricingProfile profile = pricingProfileRepository.findById(pricingProfileId);
        if (profile == null) {
            throw new BizCheckFailException("定价画像不存在: " + pricingProfileId);
        }
        return profile;
    }

    /** 画像 BCR Root。 */
    BusinessChangeRecord getRoot(PricingProfile profile) {
        return getRoot(BusinessChangeRecordRecordType.PAYMENT_METHOD_PROFILE, profile.getPricingProfileId());
    }

    /** 条件行 (维度, 值) 不重复。 */
    static void checkDistinct(List<Condition> conditions) {
        if (new HashSet<>(conditionKeysOf(conditions)).size() != conditions.size()) {
            throw new InvalidParameterException("conditions 不得重复");
        }
    }

    /** 请求条件的 (维度, 值) 键。 */
    static List<String> conditionKeysOf(List<Condition> conditions) {
        return conditions.stream().map(cond -> cond.getDimensionCode().getCode() + ":" + cond.getConditionValue()).toList();
    }

    /** 已保存条件的 (维度, 值) 集合。 */
    static Set<String> conditionKeys(List<PricingProfileCond> conditions) {
        return conditions.stream().map(cond -> cond.getDimensionCode().getCode() + ":" + cond.getConditionValue())
            .collect(Collectors.toSet());
    }

    /** 条件新行。 */
    List<PricingProfileCond> buildConditions(long pricingProfileId, List<Condition> conditions, Instant now) {
        return conditions.stream().map(condition -> {
            PricingProfileCond row = new PricingProfileCond();
            row.setProfileConditionId(idGenerator.nextId());
            row.setPricingProfileId(pricingProfileId);
            row.setDimensionCode(condition.getDimensionCode());
            row.setConditionValue(condition.getConditionValue());
            row.setCreateTime(now);
            row.setUpdateTime(now);
            return row;
        }).toList();
    }

    /** 从上下文构造画像结果。 */
    static PricingProfileView view(PricingProfileContext context) {
        return new PricingProfileView(context.getProfile(), context.getConditions(), context.getRoot().getCurrentVersion());
    }
}
