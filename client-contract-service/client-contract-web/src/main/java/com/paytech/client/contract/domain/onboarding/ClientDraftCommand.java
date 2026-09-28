package com.paytech.client.contract.domain.onboarding;
import java.util.*;
import java.security.*;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import tools.jackson.databind.json.JsonMapper;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 同一账号下的稳定命令身份；摘要仅用于同键异参检查。 */
public record ClientDraftCommand(long accountId, String operation, String key, String hash) {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public static long id(String value) {
        try {
            if (value==null || !value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) { throw new FailException(INVALID_DRAFT); }
    }
    public static ClientDraftCommand of(long accountId, String operation, String key, Map<String,?> body) {
        if (key==null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new FailException(INVALID_DRAFT);
        return new ClientDraftCommand(accountId,operation,key.toLowerCase(Locale.ROOT),digest(Map.of(
            "domain","CCT_CLIENT_DRAFT_COMMAND_1","accountId",Long.toString(accountId),"operation",operation,"body",body)));
    }
    public String scope() { return "CLIENT_ACCOUNT:"+accountId; }
    public static String digest(Map<String,?> value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(value))));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
