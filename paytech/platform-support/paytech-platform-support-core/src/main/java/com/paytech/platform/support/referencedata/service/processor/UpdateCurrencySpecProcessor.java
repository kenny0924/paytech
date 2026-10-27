package com.paytech.platform.support.referencedata.service.processor;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;
import com.paytech.platform.support.referencedata.domain.CurrencyAttributes;
import com.paytech.platform.support.referencedata.domain.CurrencySpecAuditFacts;
import com.paytech.platform.support.referencedata.domain.UpdateCurrencySpecContext;
import com.paytech.platform.support.referencedata.domainservice.service.CurrencySpecManagementService;
import com.paytech.platform.support.referencedata.service.processor.request.UpdateCurrencySpecRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CurrencySpecMutation;
import com.paytech.platform.support.service.processor.OperationsTransactionExecutor;

import lombok.AllArgsConstructor;

/**
 * 按原版本修改币种的数字代码、名称、符号、小数位、舍入方式与 logo
 * <li>启用后冻结（S-02）：ACTIVE、INACTIVE 币种的数字代码、名称、符号、小数位与舍入方式与库中不同时返回 422，字段错误逐项写明冻结字段；
 * logo 任何状态都可修改。</li>
 * <li>换成另一个 logo 时核对文件存在且为公开文件，否则 422（字段 logoFileAddress）；文件元数据经文件 Owner 的同库能力读取。</li>
 * <li>无变化时不递增版本、不登记审计；版本不符返回 409，不存在返回 404。</li>
 * <li>按币种代码持资源锁，在一个本地事务内完成权限复核、行锁、版本核对、保存与审计登记。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateCurrencySpecProcessor extends AbstractLockProcessor<UpdateCurrencySpecRequest, CurrencySpecMutation> {

    /** 冻结字段的中文名，用于字段错误提示。 */
    static final Map<String, String> FROZEN_FIELD_LABELS = Map.of("numericCode", "数字代码", "name", "名称", "symbol", "符号",
        "minorUnit", "小数位", "roundingMode", "舍入方式");

    private final ReferenceDataLogos logos;

    private final OperationsAuditService audit;

    private final CurrencySpecManagementService service;

    private final CurrencySpecManagementSupport support;

    private final OperationsTransactionExecutor transactions;

    /** 与新建、启停共用币种代码资源锁。 */
    @Override
    protected LockCommand lockCommand(UpdateCurrencySpecRequest request) {
        return LockCommand.tryLock(CurrencySpecManagementSupport.lockKey(request.getCurrency()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected CurrencySpecMutation processWithinLock(UpdateCurrencySpecRequest request) {
        return transactions.write(() -> execute(request));
    }

    /** 授权、行锁与版本核对通过后构造 Context，冻结与 logo 核对通过才保存，保存成功才登记审计。 */
    CurrencySpecMutation execute(UpdateCurrencySpecRequest request) {
        long tenantId = support.authorize(request.getActor(), request.getSession());
        ReferenceCurrency currency = support.lock(request.getCurrency(), request.getExpectedVersion());
        var context = new UpdateCurrencySpecContext(currency, attributes(request), support.now());
        requireNotFrozen(context);
        if (context.unchanged()) {
            // 无变化保存不追加审计，也不递增版本。
            audit.suppressReplay();
            return CurrencySpecMutation.of(currency);
        }
        if (context.logoReplaced()) {
            logos.requirePublicFile(context.getAttributes().getLogoFileAddress());
        }
        var fact = CurrencySpecAuditFacts.updated(request.getActor(), tenantId, context, request.getReason());
        service.update(context);
        audit.record(() -> fact);
        return CurrencySpecMutation.of(currency);
    }

    /** 请求中的目标属性。 */
    static CurrencyAttributes attributes(UpdateCurrencySpecRequest request) {
        return new CurrencyAttributes(request.getNumericCode(), request.getName(), request.getSymbol(),
            request.getMinorUnit(), request.getRoundingMode(), request.getLogoFileAddress());
    }

    /** 启用过的币种修改冻结字段时逐项拒绝，提示带币种与当前状态。 */
    void requireNotFrozen(UpdateCurrencySpecContext context) {
        List<String> fields = context.frozenFieldChanges();
        if (fields.isEmpty()) {
            return;
        }
        ReferenceCurrency currency = context.getCurrency();
        throw new RequestBodyValidationException(CommonErrorCode.VALIDATION_ERROR, fields.stream()
            .map(field -> new ProblemFieldError(field, "币种 " + currency.getCurrencyCode() + " 已启用过（当前状态 "
                + currency.getStatus().getCode() + "），" + FROZEN_FIELD_LABELS.get(field) + "不可修改"))
            .toList(), null);
    }
}
