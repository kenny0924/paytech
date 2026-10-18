package com.paytech.operations.console.bff.integration.impl;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.api.response.VariableListResponseBody;
import com.paytech.risk.decision.api.response.VariableMembersResponseBody;
import com.paytech.risk.decision.api.response.VariableReferencesResponseBody;
import com.paytech.risk.decision.api.RiskVariableModels.*;
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
        require(r.getItems() != null && r.getItems().size() <= 100);
        var seen = new HashSet<String>();
        for (var item : r.getItems()) {
            require(item != null && seen.add(item.getVariableCode()));
            if (source != null) equal(source, item.getSourceType());
            if (type != null) equal(type, item.getValueType());
            if (status != null) equal(status, item.getStatus());
        }
        if (codes != null) {
            require(r.getPage() == null && r.getSize() == null && r.getTotal() == null && r.getMissingCodes() != null);
            for (String missing : r.getMissingCodes()) require(seen.add(missing));
            equal(new HashSet<>(Arrays.asList(codes.split(",", -1))), seen);
        } else {
            equal(page == null ? 1 : page, r.getPage()); equal(size == null ? 20 : size, r.getSize());
            require(r.getTotal() != null && r.getTotal() >= 0 && r.getMissingCodes() == null && r.getItems().size() <= r.getSize());
        }
    }

    static void members(VariableMembersResponseBody r, Integer page, Integer size, String expectedVersion) {
        equal(page == null ? 1 : page, r.getPage()); equal(size == null ? 50 : size, r.getSize());
        if (expectedVersion != null) equal(expectedVersion, r.getVersion());
        require(r.getItems() != null && r.getItems().size() <= r.getSize() && r.getTotal() >= 0 && r.getTotal() <= 1000);
        int previous = 0;
        for (var item : r.getItems()) {
            require(item.getSortOrder() > previous && item.getSortOrder() <= 1000);
            previous = item.getSortOrder();
        }
    }

    static void references(VariableReferencesResponseBody r, String code, Integer size) {
        require(r.getItems() != null && r.getItems().size() <= (size == null ? 20 : size));
        require(r.getScannedRows() >= 0 && r.getScannedRows() <= 1000);
        require(r.getHasMore() == (r.getNextCursor() != null));
        for (var item : r.getItems()) equal(code, item.getVariableCode());
    }
}
