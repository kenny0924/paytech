package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domain.PricingProfileContext;
import com.paytech.commercial.domain.PricingProfileView;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.domainservice.service.PricingProfileService;
import com.paytech.commercial.service.processor.request.UpdatePricingProfileRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 修改 DRAFT 定价画像（PUT /pricing-profiles/v1/{pricingProfileId}），锁 {@code pricing-profile:{id}}
 * <li>原请求：名称、Payment Method 与条件集合均与当前一致时不写入，直接返回当前版本。</li>
 * <li>新请求：期望版本一致、状态 DRAFT（ACTIVE 后冻结）；种类不可改，Payment Method 与条件维度须与种类相容。名称与 Payment Method
 * 条件更新、条件行整体替换与 Root CAS 同一事务写入。</li>
 */
public class UpdatePricingProfileProcessor
    extends AbstractPricingProfileWriteProcessor<UpdatePricingProfileRequest, PricingProfileView> {
    private final PricingProfileService pricingProfileService;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param pricingProfileService 画像写入服务
     * @param pricingProfileRepository 画像仓储
     * @param pricingProfileCondRepository 条件仓储
     * @param businessChangeRecordRepository BCR 仓储
     */
    public UpdatePricingProfileProcessor(Clock clock, IdGenerator idGenerator, PricingProfileService pricingProfileService,
        PricingProfileRepository pricingProfileRepository, PricingProfileCondRepository pricingProfileCondRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        super(clock, idGenerator, pricingProfileRepository, pricingProfileCondRepository, businessChangeRecordRepository);
        this.pricingProfileService = pricingProfileService;
    }

    /** 锁画像。 */
    @Override
    protected LockCommand lockCommand(UpdatePricingProfileRequest request) {
        return LockCommand.lock(CommercialLocks.pricingProfile(request.getPricingProfileId()));
    }

    /** 条件行不重复。 */
    @Override
    protected void validate(UpdatePricingProfileRequest request) {
        checkDistinct(request.getConditions());
    }

    @Override
    protected PricingProfileView processWithinLock(UpdatePricingProfileRequest request) {
        // 查询画像与内容是否已一致
        PricingProfile profile = getProfile(request.getPricingProfileId());
        BusinessChangeRecord root = getRoot(profile);
        List<PricingProfileCond> conditions = pricingProfileCondRepository.findByProfileId(profile.getPricingProfileId());
        if (unchanged(profile, conditions, request)) {
            return new PricingProfileView(profile, conditions, root.getCurrentVersion());
        }

        // 构建上下文
        PricingProfileContext context = buildContext(profile, root, request);

        // 保存上下文
        pricingProfileService.updateDraft(context);

        // 响应处理
        return view(context);
    }

    /** 名称、Payment Method 与条件集合是否均与当前一致。 */
    static boolean unchanged(PricingProfile profile, List<PricingProfileCond> conditions,
        UpdatePricingProfileRequest request) {
        return Objects.equals(profile.getProfileName(), request.getProfileName())
            && Objects.equals(profile.getPaymentMethodCode(), request.getPaymentMethodCode())
            && conditionKeys(conditions).equals(new HashSet<>(conditionKeysOf(request.getConditions())));
    }

    /** 校验版本与 DRAFT 状态，构造目标内容与新条件行并校验种类相容。 */
    PricingProfileContext buildContext(PricingProfile profile, BusinessChangeRecord root, UpdatePricingProfileRequest request) {
        checkVersion(root, request.getExpectedChangeVersion());
        if (profile.getStatus() != PricingProfileStatus.DRAFT) {
            throw new BizCheckFailException("只有 DRAFT 画像可以修改: " + profile.getPricingProfileCode() + ", 状态: "
                + profile.getStatus().getCode());
        }
        var now = now();
        PricingProfileContext context = new PricingProfileContext(profile, root,
            buildConditions(profile.getPricingProfileId(), request.getConditions(), now), request.getActorId(), now,
            request.getExpectedChangeVersion());
        context.setNextProfileName(request.getProfileName());
        context.setNextPaymentMethodCode(request.getPaymentMethodCode());
        context.checkDefinition(request.getPaymentMethodCode());
        return context;
    }
}
