package com.paytech.platform.support.test.dataquery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.DataQueryTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 联合查询的本地前置数据：直接插入键类型与带联合查询配置的模板，不调用被测接口。
 * <li>链路模板读取被查库 mor_data_query_fixture 的 t_jq_* 表（2026-10-29-joint-query-fixture.sql）；
 * 插入的行由 PlatformDataCleaner 整表回收。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class JointQueryMocker {

    /** 链路模板编码。 */
    public static final String REFUND_BY_NO = "JQ_REFUND_BY_NO";

    public static final String REFUND_BY_ORDER = "JQ_REFUND_BY_ORDER";

    public static final String ORDER_BY_ID = "JQ_ORDER_BY_ID";

    public static final String SESSION_BY_ID = "JQ_SESSION_BY_ID";

    public static final String INTENT_BY_ORDER = "JQ_INTENT_BY_ORDER";

    public static final String INTENT_BY_ID = "JQ_INTENT_BY_ID";

    public static final String ATTEMPT_BY_INTENT = "JQ_ATTEMPT_BY_INTENT";

    public static final String ATTEMPT_BY_ID = "JQ_ATTEMPT_BY_ID";

    public static final String ACTION_BY_ATTEMPT = "JQ_ACTION_BY_ATTEMPT";

    public static final String BILLING_BY_ORDER = "JQ_BILLING_BY_ORDER";

    public static final String ORDER_BY_CLIENT_ORDER_ID = "JQ_ORDER_BY_CLIENT_ORDER_ID";

    public static final String ORDER_BY_CLIENT = "JQ_ORDER_BY_CLIENT";

    /** 固定数据中的退款单号（订单 5001）。 */
    public static final String REFUND_NO = "rfd_A00000000000000000000000007001";

    /** 退款单号正则，与初始化数据相同。 */
    public static final String REFUND_NO_PATTERN = "rfd_[0-9A-Za-z]{30}";

    /** 1—19 位数字。 */
    public static final String ID_PATTERN = "[0-9]{1,19}";

    private final DataQueryTestMapper mapper;

    /**
     * 插入 ACTIVE 键类型。
     *
     * @return 键类型 ID
     */
    public long keyType(String code, String group, QueryKeyValueType valueType, QueryKeyRole role, boolean entry,
        String pattern, int sortNo) {
        return keyType(code, group, valueType, role, entry, pattern, sortNo, QueryKeyTypeStatus.ACTIVE);
    }

    /**
     * 插入键类型。
     *
     * @return 键类型 ID
     */
    public long keyType(String code, String group, QueryKeyValueType valueType, QueryKeyRole role, boolean entry,
        String pattern, int sortNo, QueryKeyTypeStatus status) {
        long id = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertKeyType(id, code, code + " 名称", group, valueType.getCode(), role.getCode(), entry,
            pattern, pattern == null ? null : code + " 输入提示", sortNo, status.getCode(), now()));
        return id;
    }

    /** 链路用到的键类型：收单、支付、渠道、计费分组与归属维度，顺序号同初始化数据。 */
    public void chainKeyTypes() {
        keyType("CHECKOUT_SESSION_ID", "收单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, ID_PATTERN, 10);
        keyType("ORDER_ID", "收单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, ID_PATTERN, 20);
        keyType("CLIENT_ORDER_ID", "收单", QueryKeyValueType.STRING, QueryKeyRole.LINK, true, "[!-~]{1,128}", 22);
        keyType("REFUND_REQUEST_ID", "收单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, ID_PATTERN, 30);
        keyType("REFUND_REQUEST_NO", "收单", QueryKeyValueType.STRING, QueryKeyRole.LINK, true, REFUND_NO_PATTERN, 31);
        keyType("PAYMENT_INTENT_ID", "支付", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, ID_PATTERN, 40);
        keyType("PAYMENT_ATTEMPT_ID", "支付", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, ID_PATTERN, 50);
        keyType("PAYMENT_ACTION_ID", "支付", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, ID_PATTERN, 60);
        keyType("EXTERNAL_TRANSACTION_ID", "渠道", QueryKeyValueType.STRING, QueryKeyRole.LINK, true, null, 80);
        keyType("BILLING_FACT_ID", "计费", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, ID_PATTERN, 100);
        keyType("CLIENT_ID", "归属维度", QueryKeyValueType.INTEGER, QueryKeyRole.DIMENSION, true, ID_PATTERN, 900);
        keyType("CHANNEL_INTERACTION_ID", "渠道", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, false, null, 75);
    }

    /** 退款链路的 12 个模板，全部 ACTIVE、读取同一数据源。 */
    public void chainTemplates(long dataSourceId) {
        template(REFUND_BY_NO, dataSourceId, "SELECT refund_request_id, refund_request_no, order_id, refund_amount"
            + " FROM t_jq_refund WHERE refund_request_no IN (#{refundNos}) ORDER BY refund_request_id",
            list("refundNos", "STRING"), null, none(100),
            joint("REFUND_REQUEST_ID", "refundNos", "REFUND_REQUEST_NO", "refund_request_id:REFUND_REQUEST_ID:LINK",
                "order_id:ORDER_ID:LINK"));
        template(REFUND_BY_ORDER, dataSourceId, "SELECT refund_request_id, order_id, refund_amount FROM t_jq_refund"
            + " WHERE order_id IN (#{orderIds}) ORDER BY refund_request_id", list("orderIds", "INTEGER"), null,
            none(100), joint("REFUND_REQUEST_ID", "orderIds", "ORDER_ID", "refund_request_id:REFUND_REQUEST_ID:LINK",
                "order_id:ORDER_ID:LINK"));
        template(ORDER_BY_ID, dataSourceId, "SELECT order_id, order_no, client_order_id, client_id, checkout_session_id,"
            + " buyer_email FROM t_jq_order WHERE order_id IN (#{orderIds}) ORDER BY order_id",
            list("orderIds", "INTEGER"), "[{\"name\":\"buyer_email\",\"mask\":\"FULL\",\"keepStart\":0,\"keepEnd\":0}]",
            DataQueryMocker.constraints(100, 5, QueryPagingMode.OFFSET, 50, false),
            joint("ORDER_ID", "orderIds", "ORDER_ID", "order_id:ORDER_ID:LINK",
                "checkout_session_id:CHECKOUT_SESSION_ID:LINK", "client_id:CLIENT_ID:DIMENSION",
                "client_order_id:CLIENT_ORDER_ID:DIMENSION"));
        template(SESSION_BY_ID, dataSourceId, "SELECT checkout_session_id, order_id, session_status"
            + " FROM t_jq_checkout_session WHERE checkout_session_id = #{sessionId}", scalar("sessionId", "INTEGER"),
            null, none(100), joint("CHECKOUT_SESSION_ID", "sessionId", "CHECKOUT_SESSION_ID",
                "checkout_session_id:CHECKOUT_SESSION_ID:LINK", "order_id:ORDER_ID:LINK"));
        template(INTENT_BY_ORDER, dataSourceId, "SELECT payment_intent_id, source_id, intent_status"
            + " FROM t_jq_payment_intent WHERE source_type = 'COMMERCE_ORDER' AND source_id IN (#{orderIds})"
            + " ORDER BY payment_intent_id", list("orderIds", "STRING"), null, none(100),
            joint("PAYMENT_INTENT_ID", "orderIds", "ORDER_ID", "payment_intent_id:PAYMENT_INTENT_ID:LINK",
                "source_id:ORDER_ID:LINK"));
        template(INTENT_BY_ID, dataSourceId, "SELECT payment_intent_id, source_id, intent_status"
            + " FROM t_jq_payment_intent WHERE payment_intent_id IN (#{intentIds}) ORDER BY payment_intent_id",
            list("intentIds", "INTEGER"), null, none(100), joint("PAYMENT_INTENT_ID", "intentIds", "PAYMENT_INTENT_ID",
                "payment_intent_id:PAYMENT_INTENT_ID:LINK", "source_id:ORDER_ID:LINK"));
        template(ATTEMPT_BY_INTENT, dataSourceId, "SELECT payment_attempt_id, payment_intent_id, attempt_status"
            + " FROM t_jq_payment_attempt WHERE payment_intent_id IN (#{intentIds})"
            + " {{#if __cursor}}AND payment_attempt_id > #{__cursor.payment_attempt_id}{{/if}}"
            + " ORDER BY payment_attempt_id", list("intentIds", "INTEGER"), null,
            DataQueryMocker.constraints(100, 5, QueryPagingMode.CURSOR, 10, false, "payment_attempt_id"),
            joint("PAYMENT_ATTEMPT_ID", "intentIds", "PAYMENT_INTENT_ID", "payment_attempt_id:PAYMENT_ATTEMPT_ID:LINK",
                "payment_intent_id:PAYMENT_INTENT_ID:LINK"));
        template(ATTEMPT_BY_ID, dataSourceId, "SELECT payment_attempt_id, payment_intent_id, attempt_status"
            + " FROM t_jq_payment_attempt WHERE payment_attempt_id IN (#{attemptIds}) ORDER BY payment_attempt_id",
            list("attemptIds", "INTEGER"), null, none(100), joint("PAYMENT_ATTEMPT_ID", "attemptIds",
                "PAYMENT_ATTEMPT_ID", "payment_attempt_id:PAYMENT_ATTEMPT_ID:LINK",
                "payment_intent_id:PAYMENT_INTENT_ID:LINK"));
        template(ACTION_BY_ATTEMPT, dataSourceId, "SELECT payment_action_id, payment_attempt_id, external_transaction_id"
            + " FROM t_jq_payment_action WHERE payment_attempt_id IN (#{attemptIds}) ORDER BY payment_action_id",
            list("attemptIds", "INTEGER"), null, none(100), joint("PAYMENT_ACTION_ID", "attemptIds",
                "PAYMENT_ATTEMPT_ID", "payment_action_id:PAYMENT_ACTION_ID:LINK",
                "payment_attempt_id:PAYMENT_ATTEMPT_ID:LINK", "external_transaction_id:EXTERNAL_TRANSACTION_ID:LINK"));
        template(BILLING_BY_ORDER, dataSourceId, "SELECT billing_fact_id, order_ref, fact_amount FROM t_jq_billing_fact"
            + " WHERE order_ref IN (#{orderRefs}) ORDER BY billing_fact_id", list("orderRefs", "STRING"), null,
            none(100), joint("BILLING_FACT_ID", "orderRefs", "ORDER_ID", "billing_fact_id:BILLING_FACT_ID:LINK",
                "order_ref:ORDER_ID:LINK"));
        template(ORDER_BY_CLIENT_ORDER_ID, dataSourceId, "SELECT order_id, client_order_id, client_id FROM t_jq_order"
            + " WHERE client_order_id IN (#{clientOrderIds}) ORDER BY order_id", list("clientOrderIds", "STRING"), null,
            none(100), joint("ORDER_ID", "clientOrderIds", "CLIENT_ORDER_ID", "order_id:ORDER_ID:LINK",
                "client_id:CLIENT_ID:DIMENSION"));
        template(ORDER_BY_CLIENT, dataSourceId, "SELECT order_id, client_id FROM t_jq_order"
            + " WHERE client_id = #{clientId} ORDER BY order_id", scalar("clientId", "INTEGER"), null, none(100),
            joint("ORDER_ID", "clientId", "CLIENT_ID", "order_id:ORDER_ID:LINK", "client_id:CLIENT_ID:DIMENSION"));
    }

    /**
     * 插入 ACTIVE 模板与带联合查询配置的版本 1。
     *
     * @return 模板 ID
     */
    public long template(String code, long dataSourceId, String body, String parameters, String columns,
        String constraints, String joint) {
        return template(code, dataSourceId, body, parameters, columns, constraints, joint, QueryTemplateStatus.ACTIVE);
    }

    /**
     * 插入模板与带联合查询配置的版本 1。
     *
     * @return 模板 ID
     */
    public long template(String code, long dataSourceId, String body, String parameters, String columns,
        String constraints, String joint, QueryTemplateStatus status) {
        long id = OperatorBuilder.nextId();
        Instant now = now();
        assertEquals(1, mapper.insertTemplate(id, code, dataSourceId, status.getCode(), now));
        assertEquals(1, mapper.insertJointVersion(OperatorBuilder.nextId(), id, dataSourceId, body, parameters, columns,
            constraints, joint, now));
        return id;
    }

    /** 必填 LIST 参数声明。 */
    public static String list(String name, String elementType) {
        return "[{\"name\":\"" + name + "\",\"type\":\"LIST\",\"elementType\":\"" + elementType
            + "\",\"required\":true}]";
    }

    /** 必填标量参数声明。 */
    public static String scalar(String name, String type) {
        return "[{\"name\":\"" + name + "\",\"type\":\"" + type + "\",\"required\":true}]";
    }

    /** 不分页约束，超时 5 秒。 */
    public static String none(int maxRows) {
        return DataQueryMocker.constraints(maxRows, 5, QueryPagingMode.NONE, null, false);
    }

    /**
     * joint_json 原文。
     *
     * @param columns 形如 name:KEY_TYPE:LINK
     * @return JSON
     */
    public static String joint(String subject, String keyName, String keyType, String... columns) {
        String items = Arrays.stream(columns).map(column -> column.split(":"))
            .map(parts -> "{\"name\":\"" + parts[0] + "\",\"keyType\":\"" + parts[1] + "\",\"keyRole\":\"" + parts[2]
                + "\"}").collect(Collectors.joining(","));
        return "{\"subjectKeyType\":\"" + subject + "\",\"keyParameter\":{\"name\":\"" + keyName + "\",\"keyType\":\""
            + keyType + "\"},\"columns\":[" + items + "]}";
    }

    /** 与 MySQL DATETIME(6) 一致的 UTC 微秒时间。 */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
