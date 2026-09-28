package com.paytech.finance.auto;

import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.finance.api.balance.BalanceModels.ClientBalanceResponseBody;
import com.paytech.finance.api.balance.BalanceModels.PartnerBalanceResponseBody;
import com.paytech.finance.api.balance.BalanceQueryApi;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;

import tools.jackson.databind.json.JsonMapper;

/** 契约结构与 JSON 精度测试；不代表 Controller、授权或数据库查询已经实现。 */
class BalanceQueryContractAutoTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void test_twoOwnerSpecificGetContracts() throws Exception {
        Assertions.assertEquals("/balances/v1", BalanceQueryApi.class.getAnnotation(HttpExchange.class).value());
        Assertions.assertEquals(2, BalanceQueryApi.class.getDeclaredMethods().length);
        assertGet("queryClientBalance", "clientId", ClientBalanceResponseBody.class);
        assertGet("queryPartnerBalance", "partnerId", PartnerBalanceResponseBody.class);
    }

    @Test
    void test_clientAmountsAndIdsRetainPrecision() {
        ClientBalanceResponseBody value =
            new ClientBalanceResponseBody("9007199254740993", "101", "USD", Money.of("USD", "9999999999999999.99"),
                Money.of("USD", "70.00"), Money.of("USD", "30.00"), Money.of("USD", "500.00"), Money.of("USD", "0.00"));
        String json = mapper.writeValueAsString(value);
        var tree = mapper.readTree(json);
        Assertions.assertTrue(tree.get("clientId").isString());
        Assertions.assertEquals("9007199254740993", tree.get("clientId").asString());
        for (String field : Set.of("pendingAmount", "availableAmount", "lockedAmount", "securityDepositAmount",
            "recoveryReceivableAmount")) {
            Assertions.assertTrue(tree.get(field).get("amount").isString(), field);
        }
        Assertions.assertEquals("9999999999999999.99", tree.get("pendingAmount").get("amount").asString());
        Assertions.assertEquals(value, mapper.readValue(json, ClientBalanceResponseBody.class));
    }

    @Test
    void test_partnerHasNoClientDepositOrDerivedTotal() {
        PartnerBalanceResponseBody value = new PartnerBalanceResponseBody("201", "101", "USD", Money.of("USD", "10.00"),
            Money.of("USD", "20.00"), Money.of("USD", "5.00"), Money.of("USD", "2.00"));
        String json = mapper.writeValueAsString(value);
        var tree = mapper.readTree(json);
        Assertions.assertFalse(tree.has("securityDepositAmount"));
        Assertions.assertFalse(tree.has("clientId"));
        Assertions.assertFalse(tree.has("totalAmount"));
        Assertions.assertEquals("2", tree.get("recoveryReceivableAmount").get("amount").asString());
        Assertions.assertEquals(value, mapper.readValue(json, PartnerBalanceResponseBody.class));
    }

    @Test
    void test_reverseBalanceIsNotConvertedToAbsoluteValue() {
        PartnerBalanceResponseBody value = new PartnerBalanceResponseBody("201", "101", "USD", Money.of("USD", "0.00"),
            Money.of("USD", "-1.00"), Money.of("USD", "0.00"), Money.of("USD", "0.00"));
        Assertions.assertEquals("-1",
            mapper.readTree(mapper.writeValueAsString(value)).get("availableAmount").get("amount").asString());
    }

    @Test
    void test_balanceCurrenciesCannotDisagreeOrBeMissing() {
        Money usd = Money.zero("USD");
        Assertions.assertThrows(InvalidParameterException.class, () ->
            new PartnerBalanceResponseBody("201", "101", "USD", usd, Money.zero("AED"), usd, usd));
        Assertions.assertThrows(InvalidParameterException.class, () ->
            new ClientBalanceResponseBody("201", "101", "USD", usd, usd, usd, null, usd));
        Assertions.assertThrows(InvalidParameterException.class, () ->
            new PartnerBalanceResponseBody("201", "101", null, usd, usd, usd, usd));
    }

    private void assertGet(String name, String owner, Class<?> responseType) throws Exception {
        Method method = BalanceQueryApi.class.getMethod(name, String.class, String.class, String.class);
        String resource = owner.equals("clientId") ? "clients" : "partners";
        Assertions.assertEquals("/" + resource + "/{" + owner + "}", method.getAnnotation(GetExchange.class).value());
        Assertions.assertEquals(responseType, method.getReturnType());
        var parameters = method.getParameters();
        Assertions.assertEquals(owner, parameters[0].getAnnotation(PathVariable.class).value());
        Assertions.assertEquals("operatingEntityId", parameters[1].getAnnotation(RequestParam.class).value());
        Assertions.assertEquals("currency", parameters[2].getAnnotation(RequestParam.class).value());
        Assertions.assertTrue(parameters[1].getAnnotation(RequestParam.class).required());
        Assertions.assertTrue(parameters[2].getAnnotation(RequestParam.class).required());
    }
}
