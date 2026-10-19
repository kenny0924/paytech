package com.paytech.commercial.service.processor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.constant.CommercialLocks;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.domainservice.validator.term.TermPriceSetValidator;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.commercial.service.processor.request.ReplaceCommercialTermPricesRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 整体替换 DRAFT 版本的价格集合（technique.md §2.2）
 * <li>写入校验：OE 被版本合同覆盖、画像存在、同 OE + 币种画像与优先级唯一、结算字段与条款类型匹配、计费事件已登记、组件矩阵；
 * 档位连续、画像 ACTIVE 与本期取值限制在排期 / 激活时重新校验。</li>
 * <li>方案锁持续等待；根 CAS 后按档位 → 组件 → 价格删除旧集合、按价格 → 组件 → 档位插入新集合，版本 Child 记 UPDATE 快照。</li>
 */
@Component
@AllArgsConstructor
public class ReplaceCommercialTermPricesProcessor
    extends AbstractLockProcessor<ReplaceCommercialTermPricesRequest, CommercialTermVersionView> {
    private final CommercialTermLoader loader;
    private final CommercialTermService commercialTermService;
    private final PricingProfileRepository pricingProfileRepository;
    private final ProductFeeEventRepository productFeeEventRepository;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(ReplaceCommercialTermPricesRequest request) {
        return LockCommand.lock(CommercialLocks.termPlan(loader.planIdOf(request.getVersionId())));
    }

    @Override
    protected CommercialTermVersionView processWithinLock(ReplaceCommercialTermPricesRequest request) {
        // 版本须为 DRAFT
        CommercialTermVersion version = loader.version(request.getVersionId());
        if (version.getStatus() != CommercialTermVersionStatus.DRAFT) {
            throw new BizCheckFailException("只有 DRAFT 版本可以修改价格: " + version.getTermVersionId() + ", 状态 "
                + version.getStatus().getCode());
        }

        // 构建上下文并校验新集合
        CommercialTermVersionContext context = buildContext(request, version);
        TermPriceSetValidator.check(context);

        // 保存
        commercialTermService.replacePrices(context);
        return context.toVersionView();
    }

    /** 公共部分、旧行 ID、画像、计费事件与新行。 */
    CommercialTermVersionContext buildContext(ReplaceCommercialTermPricesRequest request, CommercialTermVersion version) {
        CommercialTermVersionContext.CommercialTermVersionContextBuilder builder = loader.commandContext(
            CommercialTermVersionCommandRequest.operator(request.getVersionId(), request.getExpectedChangeVersion(),
                request.getOperatorAccountId()), version);
        List<CommercialTermPrice> replaced = loader.prices(version.getTermVersionId());
        builder.replacedPriceIds(replaced.stream().map(CommercialTermPrice::getTermPriceId).toList())
            .replacedComponentIds(loader.components(replaced).stream().map(CommercialTermComponent::getTermComponentId)
                .toList())
            .profiles(pricingProfileRepository.findByCodes(request.getPrices().stream()
                .map(ReplaceCommercialTermPricesRequest.PriceItem::getPricingProfileCode).collect(Collectors.toSet())));
        CommercialTermVersionContext base = builder.build();
        builder.feeEvents(productFeeEventRepository.findByTermType(base.getPlan().getTermType()));
        addRows(builder, request, version, base);
        return builder.build();
    }

    /** 由请求项构造新价格、组件与档位（列表顺序即序号）。 */
    void addRows(CommercialTermVersionContext.CommercialTermVersionContextBuilder builder,
        ReplaceCommercialTermPricesRequest request, CommercialTermVersion version, CommercialTermVersionContext base) {
        for (ReplaceCommercialTermPricesRequest.PriceItem item : request.getPrices()) {
            long priceId = loader.nextId();
            builder.price(TermPriceRows.price(item, priceId, version.getTermVersionId(), base.getNow()));
            for (int componentIndex = 0; componentIndex < item.getComponents().size(); componentIndex++) {
                ReplaceCommercialTermPricesRequest.ComponentItem component = item.getComponents().get(componentIndex);
                long componentId = loader.nextId();
                builder.component(TermPriceRows.component(component, componentIndex + 1, componentId, priceId, base.getNow()));
                for (int bandIndex = 0; bandIndex < component.getBands().size(); bandIndex++) {
                    builder.band(TermPriceRows.band(component.getBands().get(bandIndex), bandIndex + 1, loader.nextId(),
                        componentId, base.getNow()));
                }
            }
        }
    }
}
