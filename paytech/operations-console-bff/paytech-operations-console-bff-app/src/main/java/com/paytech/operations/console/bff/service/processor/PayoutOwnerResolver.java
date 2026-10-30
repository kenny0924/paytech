package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.result;
import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.payout.response.PayoutOwnerResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;

import lombok.AllArgsConstructor;

/**
 * 收款账户 Owner 的主档摘要批量补全（client-master，S-02）：同页去重后并发读取，共用 3 秒期限
 * <li>账户 Owner 与共用同一账号的其他 Owner 一起读取（O1 共用提示的名称）。</li>
 * <li>Client 读 Client 摘要，Local Partner 读 Partner 详情；不存在、失败或超时的 Owner 不在结果中，页面显示 Owner 标识。</li>
 * <li>操作人会话或权限失效不降级，整页失败。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PayoutOwnerResolver {
    private final EntityAdminClient entityAdmin;

    /**
     * 读取一组 Owner 的主档摘要
     *
     * @param operator 当前操作人
     * @param owners Owner 键（{@link #key}），允许重复
     * @return Owner 键到摘要；读取失败的 Owner 不在结果中
     */
    public Map<String, PayoutOwnerResponseBody> resolve(OperatorContext operator, Stream<String> owners) {
        var keys = owners.distinct().toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(OperatingEntityDetailsProcessor.DEADLINE_SECONDS);
            var futures = new LinkedHashMap<String, Future<PayoutOwnerResponseBody>>();
            keys.forEach(key -> futures.put(key, executor.submit(withRequestContext(() -> load(operator, key)))));
            var resolved = new HashMap<String, PayoutOwnerResponseBody>();
            futures.forEach((key, future) -> {
                var owner = result(future, deadline);
                if (owner != null) {
                    resolved.put(key, owner);
                }
            });
            return resolved;
        }
    }

    /**
     * 账户本身与共用同一账号的其他 Owner 的键
     *
     * @param account payout 运营视图账户
     * @return Owner 键（账户 Owner 在前）
     */
    public static Stream<String> keys(BeneficiaryAccountResponseBody account) {
        return Stream.concat(Stream.of(key(account.getOwnerType(), account.getOwnerId())), sharedKeys(account));
    }

    /**
     * 共用提示中其他 Owner 的主档摘要
     *
     * @param account payout 运营视图账户
     * @param resolved 已读取的 Owner 摘要
     * @return 共用 Owner 键到摘要；读取失败的 Owner 不在结果中，没有共用时为空映射
     */
    public static Map<String, PayoutOwnerResponseBody> sharedProfiles(BeneficiaryAccountResponseBody account,
        Map<String, PayoutOwnerResponseBody> resolved) {
        var profiles = new LinkedHashMap<String, PayoutOwnerResponseBody>();
        sharedKeys(account).filter(resolved::containsKey).forEach(key -> profiles.put(key, resolved.get(key)));
        return profiles;
    }

    /** 共用提示中其他 Owner 的键。 */
    static Stream<String> sharedKeys(BeneficiaryAccountResponseBody account) {
        return account.getSharedOwners() == null ? Stream.empty()
            : account.getSharedOwners().stream().map(shared -> key(shared.getOwnerType(), shared.getOwnerId()));
    }

    /** Owner 键：类型与标识。 */
    public static String key(String ownerType, String ownerId) {
        return ownerType + ":" + ownerId;
    }

    /** 按 Owner 类型读取主档；未知类型不读取。 */
    PayoutOwnerResponseBody load(OperatorContext operator, String key) {
        String[] parts = key.split(":", 2);
        if (BeneficiaryOwnerType.CLIENT.getCode().equals(parts[0])) {
            var client = entityAdmin.clientSummary(operator, parts[1]);
            return new PayoutOwnerResponseBody(parts[0], parts[1], client.getClientNo(), client.getLegalName(),
                client.getStatus());
        }
        if (BeneficiaryOwnerType.LOCAL_PARTNER.getCode().equals(parts[0])) {
            var partner = entityAdmin.localPartner(operator, parts[1]);
            return new PayoutOwnerResponseBody(parts[0], parts[1], partner.getPartnerNo(), partner.getLegalName(),
                partner.getStatus());
        }
        return null;
    }
}
