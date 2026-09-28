package com.paytech.operations.console.bff.integration.impl;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.api.decision.response.VariableListResponseBody;
import com.paytech.risk.api.decision.response.VariableMembersResponseBody;
import com.paytech.risk.api.decision.response.VariableReferencesResponseBody;
import com.paytech.risk.api.decision.RiskVariableModels.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

/** 仅校验查询身份、分页及写入回执，值域和引用删除资格由 Risk 判断。 */
final class RiskVariableResponsePolicy {
    private RiskVariableResponsePolicy() { }

    static void require(boolean valid) {
        if (!valid) throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
    }

    static void equal(Object expected, Object actual) { require(Objects.equals(expected, actual)); }

    static void nextVersion(String expected, String actual) {
        require(actual != null && actual.matches("[1-9][0-9]{0,18}"));
        equal(new BigInteger(expected).add(BigInteger.ONE), new BigInteger(actual));
    }

    static void list(VariableListResponseBody r, String codes, Integer page, Integer size,
        String source, String type, String status) {
        require(r.items() != null && r.items().size() <= 100);
        var seen = new HashSet<String>();
        for (var item : r.items()) {
            require(item != null && seen.add(item.variableCode()));
            if (source != null) equal(source, item.sourceType());
            if (type != null) equal(type, item.valueType());
            if (status != null) equal(status, item.status());
        }
        if (codes != null) {
            require(r.page() == null && r.size() == null && r.total() == null && r.missingCodes() != null);
            for (String missing : r.missingCodes()) require(seen.add(missing));
            equal(new HashSet<>(Arrays.asList(codes.split(",", -1))), seen);
        } else {
            equal(page == null ? 1 : page, r.page()); equal(size == null ? 20 : size, r.size());
            require(r.total() != null && r.total() >= 0 && r.missingCodes() == null && r.items().size() <= r.size());
        }
    }

    static void members(VariableMembersResponseBody r, Integer page, Integer size, String expectedVersion) {
        equal(page == null ? 1 : page, r.page()); equal(size == null ? 50 : size, r.size());
        if (expectedVersion != null) equal(expectedVersion, r.version());
        require(r.items() != null && r.items().size() <= r.size() && r.total() >= 0 && r.total() <= 1000);
        int previous = 0;
        for (var item : r.items()) {
            require(item.sortOrder() > previous && item.sortOrder() <= 1000);
            previous = item.sortOrder();
        }
    }

    static void references(VariableReferencesResponseBody r, String code, Integer size) {
        require(r.items() != null && r.items().size() <= (size == null ? 20 : size));
        require(r.scannedRows() >= 0 && r.scannedRows() <= 1000);
        require(r.hasMore() == (r.nextCursor() != null));
        for (var item : r.items()) equal(code, item.variableCode());
    }
}
