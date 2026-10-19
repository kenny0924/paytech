package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;

import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.domain.PricingProfileContext;
import com.paytech.commercial.domain.PricingProfileView;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.domainservice.service.PricingProfileService;
import com.paytech.commercial.service.processor.request.CreatePricingProfileRequest;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 创建 DRAFT 定价画像（POST /pricing-profiles/v1），锁 {@code pricing-profile:{code}}；pricingProfileCode 是业务唯一身份且不可修改
 * <li>原请求：同码画像已存在时比较名称、种类、Payment Method 与条件集合，一致返回当前画像，不一致 409。</li>
 * <li>新请求：种类与 Payment Method、条件维度相容（PricingProfileContext#checkDefinition）；画像、条件行与 PAYMENT_METHOD_PROFILE
 * Root Version 1 同一事务写入。</li>
 */
public class CreatePricingProfileProcessor
    extends AbstractPricingProfileWriteProcessor<CreatePricingProfileRequest, PricingProfileView> {
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
    public CreatePricingProfileProcessor(Clock clock, IdGenerator idGenerator, PricingProfileService pricingProfileService,
        PricingProfileRepository pricingProfileRepository, PricingProfileCondRepository pricingProfileCondRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        super(clock, idGenerator, pricingProfileRepository, pricingProfileCondRepository, businessChangeRecordRepository);
        this.pricingProfileService = pricingProfileService;
    }

    /** 按不可变代码串行化创建。 */
    @Override
    protected LockCommand lockCommand(CreatePricingProfileRequest request) {
        return LockCommand.lock(CommercialLocks.pricingProfileCode(request.getPricingProfileCode()));
    }

    /** 条件行不重复。 */
    @Override
    protected void validate(CreatePricingProfileRequest request) {
        checkDistinct(request.getConditions());
    }

    @Override
    protected PricingProfileView processWithinLock(CreatePricingProfileRequest request) {
        // 原请求：同码画像已存在
        PricingProfile existing = pricingProfileRepository.findByCode(request.getPricingProfileCode());
        if (existing != null) {
            return original(existing, request);
        }

        // 构建上下文
        PricingProfileContext context = buildContext(request);

        // 保存上下文
        pricingProfileService.createProfile(context);

        // 响应处理
        return view(context);
    }

    /** 同码重放必须与原创建内容一致。 */
    PricingProfileView original(PricingProfile existing, CreatePricingProfileRequest request) {
        var conditions = pricingProfileCondRepository.findByProfileId(existing.getPricingProfileId());
        boolean same = Objects.equals(existing.getProfileName(), request.getProfileName())
            && existing.getProfileKind() == request.getProfileKind()
            && Objects.equals(existing.getPaymentMethodCode(), request.getPaymentMethodCode())
            && conditionKeys(conditions).equals(new HashSet<>(conditionKeysOf(request.getConditions())));
        if (!same) {
            throw new ConcurrentRequestException("画像代码已存在且内容不同: " + request.getPricingProfileCode());
        }
        return new PricingProfileView(existing, conditions, getRoot(existing).getCurrentVersion());
    }

    /** 构建 DRAFT 画像与条件行并校验种类相容。 */
    PricingProfileContext buildContext(CreatePricingProfileRequest request) {
        Instant now = now();
        PricingProfile profile = new PricingProfile();
        profile.setPricingProfileId(idGenerator.nextId());
        profile.setPricingProfileCode(request.getPricingProfileCode());
        profile.setProfileName(request.getProfileName());
        profile.setProfileKind(request.getProfileKind());
        profile.setPaymentMethodCode(request.getPaymentMethodCode());
        profile.setStatus(PricingProfileStatus.DRAFT);
        profile.setCreateTime(now);
        profile.setUpdateTime(now);
        PricingProfileContext context = new PricingProfileContext(profile, null,
            buildConditions(profile.getPricingProfileId(), request.getConditions(), now), request.getActorId(), now, null);
        context.checkDefinition(request.getPaymentMethodCode());
        return context;
    }
}
