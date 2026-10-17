package com.paytech.platform.support.accesscontrol.integration.signing;

import java.util.List;

/** 私钥签名与认证 HMAC 的外部边界；业务数据库只保存公开材料和 Key Ref。 */
public interface HumanAuthenticationCryptography {
    String sign(String keyRef, String kid, String publicJwk, String payloadJson);

    VersionedHmacBatch versionedHmacs(List<HmacInput> inputs);

    default VersionedHmac versionedHmac(String purpose, String value) {
        VersionedHmacBatch batch = versionedHmacs(List.of(new HmacInput(purpose, value)));
        if (batch.digests().size() != 1) {
            throw new IllegalStateException("单值 HMAC 必须只返回一个摘要");
        }
        return new VersionedHmac(batch.keyVersion(), batch.digests().getFirst());
    }

    record HmacInput(String purpose, String value) {
        public HmacInput {
            if (purpose == null || purpose.isBlank() || value == null) {
                throw new IllegalArgumentException("HMAC Purpose 与输入不能为空");
            }
        }

        @Override
        public String toString() {
            return "HmacInput[REDACTED]";
        }
    }

    record VersionedHmacBatch(int keyVersion, List<String> digests) {
        public VersionedHmacBatch {
            if (keyVersion < 1 || digests == null || digests.isEmpty()
                || digests.stream().anyMatch(digest -> digest == null || !digest.matches("[0-9a-f]{64}"))) {
                throw new IllegalArgumentException("HMAC Key版本与摘要集合不能为空");
            }
            digests = List.copyOf(digests);
        }

        @Override
        public String toString() {
            return "VersionedHmacBatch[REDACTED]";
        }
    }

    record VersionedHmac(int keyVersion, String digest) {
        public VersionedHmac {
            if (keyVersion < 1 || digest == null || digest.isBlank()) {
                throw new IllegalArgumentException("HMAC Key版本与摘要不能为空");
            }
        }

        @Override
        public String toString() {
            return "VersionedHmac[REDACTED]";
        }
    }
}
