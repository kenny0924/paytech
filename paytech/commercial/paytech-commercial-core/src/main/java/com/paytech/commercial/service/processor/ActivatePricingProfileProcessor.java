package com.paytech.commercial.service.processor;

import java.time.Clock;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.domain.PricingProfileContext;
import com.paytech.commercial.domain.PricingProfileView;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.domainservice.service.PricingProfileService;
import com.paytech.commercial.service.processor.request.PricingProfileTransitionRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 激活定价画像（POST /pricing-profiles/v1/{pricingProfileId}/activate）：DRAFT → ACTIVE，锁 {@code pricing-profile:{id}}
 * <li>原请求：已是 ACTIVE 且 Root 版本为期望版本加一时直接返回。</li>
 * <li>新请求：期望版本一致、状态 DRAFT，并按已保存内容复核种类相容；激活后种类、Payment Method 与条件永久冻结。</li>
 */
public class ActivatePricingProfileProcessor
    extends AbstractPricingProfileWriteProcessor<PricingProfileTransitionRequest, PricingProfileView> {
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
    public ActivatePricingProfileProcessor(Clock clock, IdGenerator idGenerator,
        PricingProfileService pricingProfileService, PricingProfileRepository pricingProfileRepository,
        PricingProfileCondRepository pricingProfileCondRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        super(clock, idGenerator, pricingProfileRepository, pricingProfileCondRepository, businessChangeRecordRepository);
        this.pricingProfileService = pricingProfileService;
    }

    /** 锁画像。 */
    @Override
    protected LockCommand lockCommand(PricingProfileTransitionRequest request) {
        return LockCommand.lock(CommercialLocks.pricingProfile(request.getPricingProfileId()));
    }

    @Override
    protected PricingProfileView processWithinLock(PricingProfileTransitionRequest request) {
        // 查询画像与原推进
        PricingProfile profile = getProfile(request.getPricingProfileId());
        BusinessChangeRecord root = getRoot(profile);
        PricingProfileContext context = new PricingProfileContext(profile, root,
            pricingProfileCondRepository.findByProfileId(profile.getPricingProfileId()), request.getActorId(), now(),
            request.getExpectedChangeVersion());
        if (alreadyApplied(root, request.getExpectedChangeVersion(), profile.getStatus() == PricingProfileStatus.ACTIVE)) {
            return view(context);
        }

        // 构建上下文
        prepareActivation(context);

        // 保存上下文
        pricingProfileService.changeStatus(context, BusinessChangeReason.PRICING_PROFILE_ACTIVATED);

        // 响应处理
        return view(context);
    }

    /** 校验版本与 DRAFT 状态，按已保存内容复核种类相容。 */
    static void prepareActivation(PricingProfileContext context) {
        checkVersion(context.getRoot(), context.getExpectedChangeVersion());
        PricingProfile profile = context.getProfile();
        if (profile.getStatus() != PricingProfileStatus.DRAFT) {
            throw new BizCheckFailException("只有 DRAFT 画像可以激活: " + profile.getPricingProfileCode() + ", 状态: "
                + profile.getStatus().getCode());
        }
        context.checkDefinition(profile.getPaymentMethodCode());
        context.setNextStatus(PricingProfileStatus.ACTIVE);
    }
}
