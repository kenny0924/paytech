package com.paytech.gateway.guard.concurrency;

import java.util.regex.Pattern;

import com.paytech.gateway.guard.policy.RawGuardIdentity;

/** 一个并发维度的 Lease ZSET 与 metadata Hash Key 对。 */
public record ConcurrencyKeys(String leaseKey, String metadataKey) {

    private static final int MAX_NAMESPACE_LENGTH = 128;
    private static final String GUARD_V3_MARKER = ":guard:v3:";
    private static final String LEASE_SUFFIX = ":leases";
    private static final String METADATA_SUFFIX = ":metadata";
    private static final Pattern NAMESPACE_PATTERN =
        Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*(?::[a-z0-9]+(?:-[a-z0-9]+)*)*");
    private static final Pattern EXPLICIT_VERSION_PATTERN = Pattern.compile("v[0-9]+");
    private static final Pattern ROUTE_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public ConcurrencyKeys {
        KeyParts lease = parseRoleKey(leaseKey, LEASE_SUFFIX);
        KeyParts metadata = parseRoleKey(metadataKey, METADATA_SUFFIX);
        if (!lease.baseKey().equals(metadata.baseKey())) {
            throw new IllegalArgumentException("并发 Redis Key 对必须共享同一维度 Base Key");
        }
    }

    public static ConcurrencyKeys fromBaseKey(String baseKey) {
        if (baseKey == null) {
            throw new IllegalArgumentException("并发 Redis Base Key 不可为空");
        }
        return new ConcurrencyKeys(baseKey + LEASE_SUFFIX, baseKey + METADATA_SUFFIX);
    }

    static void requireCanonicalLeaseKey(String leaseKey) {
        parseRoleKey(leaseKey, LEASE_SUFFIX);
    }

    static void requireConfiguredNamespace(ConcurrencyKeys keys, String configuredNamespace) {
        String namespace = validateConfiguredNamespace(configuredNamespace);
        KeyParts lease = parseRoleKey(keys.leaseKey(), LEASE_SUFFIX);
        KeyParts metadata = parseRoleKey(keys.metadataKey(), METADATA_SUFFIX);
        if (!lease.namespace().equals(namespace) || !metadata.namespace().equals(namespace)) {
            throw new IllegalArgumentException("并发 Redis Key 必须使用当前配置的 Namespace");
        }
    }

    static void requireConfiguredLeaseKey(String leaseKey, String configuredNamespace) {
        String namespace = validateConfiguredNamespace(configuredNamespace);
        if (!parseRoleKey(leaseKey, LEASE_SUFFIX).namespace().equals(namespace)) {
            throw new IllegalArgumentException("并发 Redis Lease Key 必须使用当前配置的 Namespace");
        }
    }

    private static KeyParts parseRoleKey(String key, String roleSuffix) {
        if (key == null || !key.endsWith(roleSuffix) || key.length() == roleSuffix.length()) {
            throw new IllegalArgumentException("并发 Redis Key 必须使用正确的角色后缀");
        }
        String baseKey = key.substring(0, key.length() - roleSuffix.length());
        int markerIndex = baseKey.indexOf(GUARD_V3_MARKER);
        if (markerIndex <= 0) {
            throw new IllegalArgumentException("并发 Redis Key 必须使用 guard:v3 前缀");
        }
        String namespace = validateConfiguredNamespace(baseKey.substring(0, markerIndex));
        String identityPath = baseKey.substring(markerIndex + GUARD_V3_MARKER.length());
        if (!isCanonicalConcurrencyIdentity(identityPath)) {
            throw new IllegalArgumentException("并发 Redis Key 维度格式无效");
        }
        return new KeyParts(namespace, baseKey);
    }

    private static String validateConfiguredNamespace(String namespace) {
        if (namespace == null || namespace.length() > MAX_NAMESPACE_LENGTH
            || !NAMESPACE_PATTERN.matcher(namespace).matches() || embedsGuardVersion(namespace)) {
            throw new IllegalArgumentException("并发 Redis Namespace 格式无效");
        }
        return namespace;
    }

    private static boolean isCanonicalConcurrencyIdentity(String identityPath) {
        int routeEnd = identityPath.indexOf(':');
        int policyEnd = routeEnd < 0 ? -1 : identityPath.indexOf(':', routeEnd + 1);
        int dimensionEnd = policyEnd < 0 ? -1 : identityPath.indexOf(':', policyEnd + 1);
        if (routeEnd <= 0 || policyEnd <= routeEnd + 1 || dimensionEnd <= policyEnd + 1
            || dimensionEnd == identityPath.length() - 1) {
            return false;
        }
        String route = identityPath.substring(0, routeEnd);
        String policy = identityPath.substring(routeEnd + 1, policyEnd);
        String dimension = identityPath.substring(policyEnd + 1, dimensionEnd);
        String rawIdentity = identityPath.substring(dimensionEnd + 1);
        if (!ROUTE_PATTERN.matcher(route).matches() || !policy.equals("concurrency")) {
            return false;
        }
        if (dimension.equals("shared-route")) {
            return rawIdentity.equals("shared");
        }
        if (!dimension.equals("client-ip") && !dimension.equals("authenticated-subject")
            && !dimension.equals("tenant")) {
            return false;
        }
        return RawGuardIdentity.isValid(rawIdentity);
    }

    private static boolean embedsGuardVersion(String namespace) {
        String[] segments = namespace.split(":", -1);
        for (int index = 0; index + 1 < segments.length; index++) {
            if (segments[index].equals("guard") && EXPLICIT_VERSION_PATTERN.matcher(segments[index + 1]).matches()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ConcurrencyKeys[leaseKeyPresent=" + (leaseKey != null) + ", metadataKeyPresent="
            + (metadataKey != null) + "]";
    }

    private record KeyParts(String namespace, String baseKey) {
    }
}
