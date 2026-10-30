package com.paytech.channel.connector.service.processor;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.api.ChannelCapabilityCodes;
import com.paytech.channel.connector.api.enums.ChannelIdentityReadinessReason;
import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.core.configuration.ChannelProperties;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.enums.CapabilityStatus;
import com.paytech.channel.connector.domain.ChannelIdentityReadiness;
import com.paytech.channel.connector.domain.OperatingEntityMids;
import com.paytech.channel.connector.domain.OperatingEntityMids.UsableMid;
import com.paytech.channel.connector.domainservice.service.OperatingEntityMidService;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.service.processor.request.ChannelIdentityReadinessItem;
import com.paytech.channel.connector.service.processor.request.MidCriteria;
import com.paytech.channel.connector.service.processor.request.QueryChannelIdentityReadinessRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 渠道身份就绪判定入口（REQ-02 technique.md §8.2）：只读配置，无副作用，不保存结论
 * <li>在只读可重复读事务内经 {@link OperatingEntityMidService} 读取 OE 的账号、MID 与能力，经渠道协议模块的 {@link ChannelService}
 * 读取渠道，再由 {@link OperatingEntityMids#usable} 筛出可用 MID（与候选查询同一筛选与一致快照），然后逐组判定。</li>
 * <li>判定顺序（最接近满足的原因）：无可用 MID → 无 ENABLED 的该支付方式能力行 → MCC 不同 → 币种既不相同也不是 * →
 * 任一体系 / 国家取值下都没有 ACTION.PAY 或 ACTION.AUTHORIZE 为 true。</li>
 * <li>第 5 步按运行期同一规则 {@code MidCapabilityContext.resolve} 逐代码取最精确值，体系 / 国家取值为该 MID 命中行出现过的值（* 以未提供即
 * null 代入，只匹配 *），因此更精确行把动作设为 false 时同样覆盖通配行。能力冲突或未注册抛错（500），不当作不就绪。</li>
 */
@Component
@AllArgsConstructor
public class QueryChannelIdentityReadinessProcessor
    extends AbstractProcessor<QueryChannelIdentityReadinessRequest, List<ChannelIdentityReadiness>> {
    /** 能承接收款的动作能力：自动请款 PAY、手动请款 AUTHORIZE（与 payment 选路一致）。 */
    static final Set<String> COLLECTION_ACTIONS = Set.of(ChannelCapabilityCodes.ACTION_PREFIX + ChannelOperation.PAY.getCode(),
        ChannelCapabilityCodes.ACTION_PREFIX + ChannelOperation.AUTHORIZE.getCode());

    private final ChannelService channelService;
    private final ChannelProperties properties;
    private final OperatingEntityMidService operatingEntityMidService;
    private final PlatformTransactionManager transactionManager;

    @Override
    protected List<ChannelIdentityReadiness> processRequest(QueryChannelIdentityReadinessRequest request) {
        var read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(status -> judge(usableMids(request.getEntityNo()), request.getItems()));
    }

    /** 读取 OE 的账号、MID 与能力及其渠道身份，筛出可用 MID；悬空关联抛 ErrorException。 */
    List<UsableMid> usableMids(String entityNo) {
        OperatingEntityMids mids = operatingEntityMidService.loadMids(entityNo);
        return mids.usable(channelService.findChannels(mids.channelIds()).stream()
            .collect(Collectors.toMap(ChannelIdentity::getChannelId, Function.identity())));
    }

    /** 按请求顺序逐组判定；同组重复只判定一次并返回同一结论。 */
    List<ChannelIdentityReadiness> judge(List<UsableMid> mids, List<ChannelIdentityReadinessItem> items) {
        Map<List<String>, ChannelIdentityReadiness> judged = new HashMap<>();
        return items.stream()
            .map(item -> judged.computeIfAbsent(item.key(), key -> new ChannelIdentityReadiness(item, reason(mids, item))))
            .toList();
    }

    /** 第一个不满足的步骤；全部满足返回 null。 */
    ChannelIdentityReadinessReason reason(List<UsableMid> mids, ChannelIdentityReadinessItem item) {
        if (mids.isEmpty()) {
            return ChannelIdentityReadinessReason.MID_NOT_FOUND;
        }
        if (noScope(mids, scope -> sameMethod(scope, item))) {
            return ChannelIdentityReadinessReason.PAYMENT_METHOD_NOT_SUPPORTED;
        }
        if (noScope(mids, scope -> sameMethod(scope, item) && sameMcc(scope, item))) {
            return ChannelIdentityReadinessReason.MCC_NOT_COVERED;
        }
        if (noScope(mids, scope -> covers(scope, item))) {
            return ChannelIdentityReadinessReason.CURRENCY_NOT_SUPPORTED;
        }
        if (mids.stream().noneMatch(mid -> collects(mid, item))) {
            return ChannelIdentityReadinessReason.PAYMENT_ACTION_NOT_SUPPORTED;
        }
        return null;
    }

    /** 所有可用 MID 都没有满足条件的能力行。 */
    static boolean noScope(List<UsableMid> mids, Predicate<MidCapability> condition) {
        return mids.stream().flatMap(mid -> mid.getCapabilities().stream()).noneMatch(condition);
    }

    /** 能力行 ENABLED 且支付方式相同。 */
    static boolean sameMethod(MidCapability scope, ChannelIdentityReadinessItem item) {
        return scope.getCapabilityStatus() == CapabilityStatus.ENABLED
            && Objects.equals(scope.getPaymentMethodCode(), item.getPaymentMethodCode());
    }

    /** 能力行 MCC 相同。 */
    static boolean sameMcc(MidCapability scope, ChannelIdentityReadinessItem item) {
        return Objects.equals(scope.getMccCode(), item.getMccCode());
    }

    /** 能力行覆盖该组合：ENABLED、支付方式与 MCC 相同、币种相同或 *（体系、国家不限）。 */
    static boolean covers(MidCapability scope, ChannelIdentityReadinessItem item) {
        return sameMethod(scope, item) && sameMcc(scope, item)
            && ("*".equals(scope.getCurrency()) || Objects.equals(scope.getCurrency(), item.getCurrency()));
    }

    /**
     * 该 MID 在某个体系 / 国家取值下按运行期规则解析出收款动作
     *
     * @param mid 可用 MID
     * @param item 判定组合
     * @return 存在一组取值使 ACTION.PAY 或 ACTION.AUTHORIZE 为 true
     *         <li>取值集合为命中行出现过的体系值与国家值（* 以 null 代入）；能力冲突或未注册时 resolve 抛 ErrorException。</li>
     */
    boolean collects(UsableMid mid, ChannelIdentityReadinessItem item) {
        List<MidCapability> hits = mid.getCapabilities().stream().filter(scope -> covers(scope, item)).toList();
        List<String> schemes = hits.stream().map(scope -> provided(scope.getSchemeCode())).distinct().toList();
        List<String> countries = hits.stream().map(scope -> provided(scope.getCountryCode())).distinct().toList();
        for (String scheme : schemes) {
            for (String country : countries) {
                if (collectsUnder(mid, criteria(item, scheme, country))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 一组完整交易维度下解析的能力含收款动作。 */
    boolean collectsUnder(UsableMid mid, MidCriteria criteria) {
        return mid.capabilityContext().resolve(criteria, properties.getCapabilityDefinitions()).stream()
            .anyMatch(capability -> COLLECTION_ACTIONS.contains(capability.getCapabilityCode())
                && Boolean.TRUE.equals(capability.getBooleanValue()));
    }

    /** 配置值 * 表示未提供（只匹配 *）。 */
    static String provided(String configured) {
        return "*".equals(configured) ? null : configured;
    }

    /** 运行期匹配用的交易维度。 */
    static MidCriteria criteria(ChannelIdentityReadinessItem item, String scheme, String country) {
        var criteria = new MidCriteria();
        criteria.setPaymentMethodCode(item.getPaymentMethodCode());
        criteria.setSchemeCode(scheme);
        criteria.setCountryCode(country);
        criteria.setCurrency(item.getCurrency());
        criteria.setMccCode(item.getMccCode());
        return criteria;
    }
}
