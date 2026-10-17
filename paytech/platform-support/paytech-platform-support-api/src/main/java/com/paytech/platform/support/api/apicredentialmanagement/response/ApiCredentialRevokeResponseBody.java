package com.paytech.platform.support.api.apicredentialmanagement.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 本次撤销返回的凭证状态及版本。 */
public class ApiCredentialRevokeResponseBody {

    /** 本次撤销的凭证标识。 */
    private final String credentialId;

    /** 固定 REVOKED。 */
    private final String status;

    /** 本次校验或保存后的凭证版本。 */
    private final int version;

    /** 按既有 JSON 属性绑定，保持字段和可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiCredentialRevokeResponseBody(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("status") String status,
        @JsonProperty("version") int version) {
        this.credentialId = credentialId;
        this.status = status;
        this.version = version;
    }

    /** 本次撤销的凭证标识。 */
    public String getCredentialId() {
        return credentialId;
    }

    /** 固定 REVOKED。 */
    public String getStatus() {
        return status;
    }

    /** 本次校验或保存后的凭证版本。 */
    public int getVersion() {
        return version;
    }
}
