package com.paytech.platform.support.referencedata.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.referencedata.core.configuration.CurrencySpecManagementProperties;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.referencedata.domainservice.repository.CurrencySpecRepository;

import lombok.AllArgsConstructor;

/** 币种规则三个写入口共用的锁参数、授权复核与当前行锁定；不访问外部能力。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CurrencySpecManagementSupport {

    /** 维护币种规则所需的 Operations 权限。 */
    public static final String MANAGE_PERMISSION = "CURRENCY_SPEC_MANAGE";

    private final Clock clock;
    private final CurrencySpecRepository repository;
    private final CurrencySpecManagementProperties properties;
    private final OperationsManagementPolicyService policy;

    /** 新建、修改与启停共用的资源锁 Key，按不可变币种代码协调。 */
    static String lockKey(String currency) {
        return "currency-spec:" + currency;
    }

    /** TRY_LOCK 等待毫秒数。 */
    int lockWaitMillis() {
        return Math.toIntExact(properties.getLockWaitTime().toMillis());
    }

    /** 资源锁租约毫秒数。 */
    int lockLeaseMillis() {
        return Math.toIntExact(properties.getLockLeaseTime().toMillis());
    }

    /**
     * 在调用方事务内重检当前会话的维护权限
     *
     * @return 操作人所属租户，用于审计主体
     */
    long authorize(String actor, String session) {
        return policy.authorizeManagement(actor, session, null, List.of(), null, null, false, MANAGE_PERMISSION)
            .member().tenantId();
    }

    /** 不存在返回公共 NOT_FOUND；存在则排他锁定并核对调用方读取的版本。 */
    CurrencySpec lock(String currency, int expectedVersion) {
        CurrencySpec spec = repository.lockByCurrency(currency);
        if (spec == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (spec.getVersion() != expectedVersion) {
            throw new ConcurrentRequestException("币种规则已被其他操作修改，请刷新后重试: " + currency + "，当前版本 "
                + spec.getVersion() + "，请求版本 " + expectedVersion);
        }
        return spec;
    }

    /** 本次保存的 UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
