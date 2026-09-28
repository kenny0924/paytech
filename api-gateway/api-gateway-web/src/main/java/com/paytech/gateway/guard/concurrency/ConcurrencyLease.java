package com.paytech.gateway.guard.concurrency;

import java.util.*;

/** 一次成功并发获取对应的不可变多维 Lease。 */
public record ConcurrencyLease(String leaseId, List<String> leaseKeys) {
    public ConcurrencyLease {
        validateLeaseId(leaseId);
        List<String> validatedLeaseKeys = Objects.requireNonNull(leaseKeys, "并发 Lease Keys 不可为空");
        if (validatedLeaseKeys.isEmpty()) {
            throw new IllegalArgumentException("并发 Lease Keys 不可为空");
        }
        Set<String> uniqueKeys = new HashSet<>();
        for (String key : validatedLeaseKeys) {
            ConcurrencyKeys.requireCanonicalLeaseKey(key);
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException("并发 Lease Key 不可重复");
            }
        }
        leaseKeys = List.copyOf(validatedLeaseKeys);
    }

    private static void validateLeaseId(String leaseId) {
        try {
            if (leaseId == null || !UUID.fromString(leaseId).toString().equals(leaseId)) {
                throw new IllegalArgumentException("并发 Lease ID 必须是规范 UUID");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("并发 Lease ID 必须是规范 UUID", exception);
        }
    }

    public List<String> keys() {
        return leaseKeys;
    }

    @Override
    public String toString() {
        return "ConcurrencyLease[keyCount=" + leaseKeys.size() + "]";
    }
}
