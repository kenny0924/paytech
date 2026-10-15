package com.paytech.client.contract.domain.onboarding;
import java.util.*;
import java.security.*;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import tools.jackson.databind.json.JsonMapper;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 同一账号下的草稿操作；HTTP 请求幂等由 Gateway 处理，这里只承载账号与操作类型。 */
public record ClientDraftCommand(long accountId, String operation) {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public static long id(String value) {
        try {
            if (value==null || !value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) { throw new FailException(INVALID_DRAFT); }
    }
    /** 账号与操作类型。 */
    public static ClientDraftCommand of(long accountId, String operation) {
        return new ClientDraftCommand(accountId,operation);
    }
    public String scope() { return "CLIENT_ACCOUNT:"+accountId; }
    public static String digest(Map<String,?> value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(value))));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
