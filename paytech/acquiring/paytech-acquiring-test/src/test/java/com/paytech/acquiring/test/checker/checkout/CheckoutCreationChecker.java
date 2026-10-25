package com.paytech.acquiring.test.checker.checkout;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.api.client.v1.enums.OrderCreationMode;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.test.dal.CheckoutCreationTestMapper;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.RoleBinding;

import lombok.AllArgsConstructor;

/** 从真实 MySQL 检查已提交事实，期望值由用例独立给出，不从响应推导金额。 */
@TestComponent
@AllArgsConstructor
public final class CheckoutCreationChecker {
    private final CheckoutCreationTestMapper mapper;

    /** 验证固定双商品场景的全部初始事实及原商品顺序。 */
    public void assertCreated(long clientId, String clientOrderId, String orderNo, Instant now, Instant expiry) {
        var order = mapper.order(clientId, clientOrderId);
        assertNotNull(order, "订单应已提交");
        assertAll(() -> assertEquals(orderNo, order.getOrderNo()), () -> assertEquals(clientId, order.getClientId()),
            () -> assertEquals(clientOrderId, order.getClientOrderId()),
            () -> assertEquals(OrderCreationMode.HOSTED_CHECKOUT, order.getCreationMode()),
            () -> assertEquals("US", order.getConsumerCountryCode()), () -> assertEquals("USD", order.getCurrency()),
            () -> assertEquals(OrderStatus.OPEN, order.getOrderStatus()),
            () -> assertAmount("44.90", order.getMerchandiseAmount()),
            () -> assertAmount("2.00", order.getDiscountAmount()), () -> assertAmount("42.90", order.getNetAmount()),
            () -> assertEquals("AE001",order.getEntityNo()),
            () -> assertAmount("0",order.getTaxAmount()), () -> assertAmount("42.90",order.getTotalAmount()),
            () -> assertAmount("0",order.getRoundingAmount()), () -> assertNull(order.getPaidAt()),
            () -> assertNull(order.getCancelledAt()), () -> assertNull(order.getTerminationRequestedAt()),
            () -> assertEquals(1L, order.getVersion()), () -> assertEquals(now, order.getCreateTime()),
            () -> assertEquals(now, order.getUpdateTime()), () -> assertEquals(expiry, order.getExpiresAt()));
        var items = mapper.items(order.getOrderId());
        assertEquals(List.of("line-1", "line-2"), items.stream().map(item -> item.clientItemId()).toList());
        var first = items.get(0);
        var second = items.get(1);
        assertAll(() -> assertEquals("Premium subscription", first.itemName()),
            () -> assertEquals("Setup service", second.itemName()), () -> assertAmount("2", first.quantity()),
            () -> assertAmount("19.95", first.unitAmount()), () -> assertAmount("39.90", first.lineGrossAmount()),
            () -> assertAmount("2.00", first.lineDiscountAmount()), () -> assertAmount("37.90", first.lineNetAmount()),
            () -> assertAmount("1", second.quantity()), () -> assertAmount("5.00", second.unitAmount()),
            () -> assertAmount("5.00", second.lineGrossAmount()), () -> assertAmount("0", second.lineDiscountAmount()),
            () -> assertAmount("5.00", second.lineNetAmount()));
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            assertEquals(index, item.sortNo());
            assertEquals(order.getOrderId(), item.orderId());
            assertEquals(now, item.createTime());
            assertEquals("USD", item.currency());
            assertEquals("SERVICE", item.productTypeCode());
            assertEquals("STANDARD", item.taxCategoryCode());
        }
        assertInitialProjections(order.getOrderId(), now);
        assertEquals(9, mapper.creationRows(order.getOrderId()), "订单、商品、会话、两种资金投影和三份准备事实");
    }

    /** 精度/上限成功场景单独验证本地金额和行数，期望仍由用例指定。 */
    public void assertAmounts(long clientId, String clientOrderId, String gross, String discount, String net,
        int itemCount) {
        var order = mapper.order(clientId, clientOrderId);
        assertNotNull(order);
        assertAll(() -> assertAmount(gross, order.getMerchandiseAmount()),
            () -> assertAmount(discount, order.getDiscountAmount()), () -> assertAmount(net, order.getNetAmount()),
            () -> assertEquals(itemCount, mapper.items(order.getOrderId()).size()));
        assertInitialProjections(order.getOrderId(), order.getCreateTime());
        assertEquals(itemCount + 7, mapper.creationRows(order.getOrderId()));
    }

    /** Session 与两种投影只是初始事实，准备完成但没有 Payment Intent。 */
    private void assertInitialProjections(long orderId, Instant now) {
        var session = mapper.session(orderId);
        var refund = mapper.refund(orderId);
        assertNotNull(session);
        assertNotNull(refund);
        assertAll(() -> assertEquals(CheckoutSessionStatus.OPEN, session.getSessionStatus()),
            () -> assertTrue(session.getPublicToken().matches("chk_[A-Za-z0-9]{30}")),
            () -> assertEquals("https://checkout.local.test", session.getPublicOrigin()),
            () -> assertEquals(orderId, session.getOrderId()), () -> assertEquals(1L, session.getVersion()),
            () -> assertEquals(now, session.getCreateTime()), () -> assertEquals(now, session.getUpdateTime()),
            () -> assertEquals(now,session.getOpenedAt()), () -> assertNull(session.getCompletedAt()),
            () -> assertNull(session.getCancelledAt()), () -> assertAmount("0", refund.getRefundableBaseAmount()),
            () -> assertAmount("0", refund.getReservedAmount()), () -> assertAmount("0", refund.getRefundedAmount()),
            () -> assertEquals("USD", refund.getCurrency()), () -> assertEquals(1L, refund.getVersion()),
            () -> assertEquals(now, refund.getCreateTime()), () -> assertEquals(now, refund.getUpdateTime()),
            () -> assertEquals(1, mapper.initialFunds(orderId, now)),
            () -> assertEquals(3, mapper.preparedFacts(orderId)));
    }

    /** 直接按已知 Order ID 检查所有创建表，包含父订单回滚后的孤立子行。 */
    public void assertCreationRowCount(long orderId, int expected) {
        assertEquals(expected, mapper.creationRows(orderId), "创建事实总行数：" + orderId);
    }

    /** 对照数据库内实际冻结的 Token 与 Origin，避免回执链接指向另一会话。 */
    public void assertCheckoutUrl(long clientId, String clientOrderId, String checkoutUrl) {
        var order = mapper.order(clientId, clientOrderId);
        assertNotNull(order);
        var session = mapper.session(order.getOrderId());
        assertNotNull(session);
        assertEquals(session.getPublicOrigin() + "/c/" + session.getPublicToken(), checkoutUrl);
    }

    /** 订单上下文冻结的 Risk 评估 ID 与期望一致；返回订单 ID 供核对评估业务身份。 */
    public long assertRiskAssessment(long clientId, String clientOrderId, long evaluationId) {
        var order = mapper.order(clientId, clientOrderId);
        assertNotNull(order, "订单应已提交");
        assertEquals(evaluationId, mapper.riskCheckoutAssessmentId(order.getOrderId()), "冻结的 Risk 评估 ID");
        return order.getOrderId();
    }

    /** 订单冻结的经营主体来源与 Local Partner。 */
    public void assertEntitySource(long clientId, String clientOrderId, String entitySourceType, Long localPartnerId) {
        var order = mapper.order(clientId, clientOrderId);
        assertNotNull(order, "订单应已提交");
        assertAll(() -> assertEquals(entitySourceType, order.getEntitySourceType(), "经营主体来源"),
            () -> assertEquals(localPartnerId, order.getLocalPartnerId(), "Local Partner"));
    }

    /** 订单上下文与法律事实中商业解析冻结的列逐项等于期望；只比较给出的列，缺少的列视为失败。 */
    public void assertFrozenCommercial(long clientId, String clientOrderId, Map<String, Object> expected) {
        var order = mapper.order(clientId, clientOrderId);
        assertNotNull(order, "订单应已提交");
        var actual = mapper.frozenCommercial(order.getOrderId());
        assertNotNull(actual, "冻结上下文应已提交");
        assertAll(expected.entrySet().stream().map(entry -> (Executable)() -> {
            assertTrue(actual.containsKey(entry.getKey()) || entry.getValue() == null, "缺少冻结列：" + entry.getKey());
            assertEquals(entry.getValue(), actual.get(entry.getKey()), "冻结列 " + entry.getKey());
        }));
    }

    /**
     * 商业关系快照的冻结结果：上下文三列与绑定行（数量、顺序、主体与版本）逐项等于期望
     * <li>没有快照时期望 fundFlow / snapshotVersion 为空、生效版本 0、绑定为空集合。</li>
     */
    public void assertRelationship(long clientId, String clientOrderId, String fundFlow, int effectiveVersion,
        Integer snapshotVersion, List<RoleBinding> expected) {
        var order = mapper.order(clientId, clientOrderId);
        assertNotNull(order, "订单应已提交");
        var context = mapper.frozenCommercial(order.getOrderId());
        var rows = mapper.relationshipBindings(order.getOrderId());
        assertAll(() -> assertEquals(fundFlow, context.get("fund_flow"), "资金流向"),
            () -> assertEquals(effectiveVersion, context.get("effective_relationship_model_version"), "生效模型版本"),
            () -> assertEquals(snapshotVersion, context.get("snapshot_model_version"), "快照推导规则版本"),
            () -> assertEquals(expected.size(), rows.size(), "角色绑定行数"));
        for (int index = 0; index < expected.size(); index++) {
            var want = expected.get(index);
            var row = rows.get(index);
            assertAll(() -> assertEquals(want.getRole(), row.getRoleCode(), "角色"),
                () -> assertEquals(want.getSubjectType(), row.getSubjectType(), "主体类型 " + want.getRole()),
                () -> assertEquals(want.getSubjectId(), row.getSubjectId(), "主体标识 " + want.getRole()),
                () -> assertEquals(want.getPartyId(), row.getPartyId() == null ? null : row.getPartyId().toString(),
                    "法律 Party " + want.getRole()),
                () -> assertEquals((Object)snapshotVersion, row.getSnapshotModelVersion(), "行快照版本 " + want.getRole()),
                () -> assertEquals(order.getCreateTime(), row.getCreateTime(), "行创建时间 " + want.getRole()));
        }
    }

    /** 失败用例不能产生订单。 */
    public void assertAbsent(long clientId, String clientOrderId) {
        assertNull(mapper.order(clientId, clientOrderId), "不应写入订单");
    }

    /** 校验本测试环境的全量订单数量，不仅检查一个响应关联的订单。 */
    public void assertOrderCount(long clientId, long otherClientId, int count) {
        assertEquals(count, mapper.orderIds(clientId, otherClientId).size());
    }

    /** 重复请求不能改写已占用的订单或补建新关联。 */
    public void assertExistingUnchanged(long clientId, String clientOrderId, long orderId, OrderStatus status,
        Instant now) {
        var order = mapper.order(clientId, clientOrderId);
        assertNotNull(order);
        assertAll(() -> assertEquals(orderId, order.getOrderId()), () -> assertEquals(status, order.getOrderStatus()),
            () -> assertEquals(1L, order.getVersion()), () -> assertEquals(now, order.getUpdateTime()),
            () -> assertAmount("44.90", order.getMerchandiseAmount()),
            () -> assertAmount("2.00", order.getDiscountAmount()), () -> assertAmount("42.90", order.getNetAmount()),
            () -> assertEquals(1, mapper.creationRows(orderId)));
    }

    /** 金额按十进制数值比较，数据库存储 scale 不改变业务金额。 */
    private void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "金额应为 " + expected + "，实际 " + actual);
    }
}
