package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.integration.OnboardingAdminClient;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody;

import lombok.AllArgsConstructor;

/**
 * 税务查询页标题行的名称补充：并发读取 client-master 的 Client 与 Operating Entity 法定名称
 * <li>不经聚合扩大访问：client-master 管理接口只核对会话存在、不做查看授权，所以每一路先以操作人会话向 PLT-I02 核对 Client 详情页
 * （operations-client-admin-detail）或经营主体详情页（operations-operating-entity-detail）的路由权限；没有权限就不调用 client-master，
 * 该名称为 null。只有 TAX_VIEW 的操作人因此只看到 ID。</li>
 * <li>只为展示；tax 已给出 ID，任何失败（不存在、超时、无权限、依赖不可用）都降级为 null，页面继续显示 ID，不让整页失败。</li>
 * <li>两路共享 2 秒等待上限，子线程沿用当前请求 ID；BFF 无数据库，调用不在任何事务内。</li>
 * <li>纳税主体名称只在纳税主体 Party 与收单 Operating Entity 的 Party 相同时填写，避免把收单主体名称错标为纳税主体。</li>
 */
@Component
@AllArgsConstructor
public class TaxOrderPartyNames {
    /** 名称读取的总等待上限。 */
    static final long DEADLINE_SECONDS = 2;

    /** Client 详情页路由码（块 138）；有其权限才可看 Client 法定名称。 */
    static final String CLIENT_DETAIL_ROUTE = "operations-client-admin-detail";

    /** 经营主体详情页路由码（块 137）；有其权限才可看 Operating Entity 法定名称。 */
    static final String OPERATING_ENTITY_DETAIL_ROUTE = "operations-operating-entity-detail";

    private final EntityAdminClient entityAdmin;
    private final OnboardingAdminClient onboardingAdmin;
    private final PlatformOperationsClient platform;

    /**
     * 为页面模型补上 Client、Operating Entity 与纳税主体名称
     *
     * @param page 已由 tax 事实组装、带 buyerClientId / entityNo / taxSubjectPartyId 的页面模型
     * @param operator 当前运营身份（client-master 管理接口要求）
     */
    public void fill(TaxOrderPageResponseBody page, OperatorContext operator) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
            var client = page.getBuyerClientId() == null ? null : executor.submit(withRequestContext(
                () -> permitted(operator, CLIENT_DETAIL_ROUTE)
                    ? onboardingAdmin.client(operator, page.getBuyerClientId()).getClient().getLegalName() : null));
            var entity = page.getEntityNo() == null ? null : executor.submit(withRequestContext(
                () -> permitted(operator, OPERATING_ENTITY_DETAIL_ROUTE)
                    ? entityAdmin.operatingEntity(operator, page.getEntityNo()) : null));
            page.setBuyerClientName(orNull(client, deadline));
            var entityDetail = orNull(entity, deadline);
            if (entityDetail != null) {
                page.setEntityName(entityDetail.getLegalName());
                page.setTaxSubjectName(Objects.equals(entityDetail.getPartyId(), page.getTaxSubjectPartyId())
                    ? entityDetail.getLegalName() : null);
            }
        }
    }

    /**
     * 以操作人会话核对是否可访问某个详情页路由（与该页面自身的 Gateway 绑定权限相同）
     * <li>拒绝或授权服务不可用都按无权限处理：名称只是展示补充，不能因授权失败而放宽，也不让整页失败。</li>
     *
     * @param operator 当前运营身份
     * @param routeCode 详情页路由码
     * @return 有权限为 true
     */
    boolean permitted(OperatorContext operator, String routeCode) {
        try {
            platform.authorize(new OperatorContext(operator.accountId(), operator.sessionId(), routeCode));
            return true;
        } catch (RuntimeException deniedOrUnavailable) {
            return false;
        }
    }

    /** 等待一路结果；任何失败或超时返回 null（展示降级，不传播）。 */
    static <T> T orNull(Future<T> future, long deadline) {
        if (future == null) {
            return null;
        }
        try {
            return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (TimeoutException | ExecutionException unavailable) {
            future.cancel(true);
            return null;
        }
    }
}
