package com.paytech.platform.support.api.authentication.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前 Realm 的完整 JSON Web Key Set 响应。 */
public final class JsonWebKeySetResponseBody {
    /** 当前可用于验签的公开密钥集合。 */
    private final List<JsonWebKeyResponseBody> keys;

    /** 保存完整公开响应，集合使用不可变副本。 */
    @JsonCreator
    public JsonWebKeySetResponseBody(@JsonProperty("keys") List<JsonWebKeyResponseBody> keys) {
        this.keys = List.copyOf(keys);
    }

    /** 当前可用于验签的公开密钥集合。 */
    public List<JsonWebKeyResponseBody> getKeys() {
        return keys;
    }
}
