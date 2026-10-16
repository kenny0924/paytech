package com.paytech.finance.accounting.domain;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * 投影重建解决摘要 {@value #SCHEMA}
 * <li>对象字段：schema, exceptionId, accountId, makerId, checkerId, before, after；before/after 各含 debitTotal,
 * creditTotal, netBalance, lastJournalSequence。</li>
 * <li>ID 与序号写字符串，金额按 minorUnit 补齐后写字符串。</li>
 * <li>先用 Jackson 写出 JSON，再经 RFC 8785 规范化后计算 SHA-256 小写十六进制。</li>
 */
public final class ProjectionRebuildContentHash {
    /** 摘要输入的版本标识，变更输入结构时必须新增版本。 */
    public static final String SCHEMA = "CODE-MOR:FINANCE:PROJECTION_REBUILD:1";

    private static final JsonFactory JSON = new JsonFactory();

    private ProjectionRebuildContentHash() {
    }

    /**
     * 计算投影重建解决摘要
     *
     * @param exception 已填好引用账户、编制人与复核人的异常
     * @param before 重建前投影
     * @param after 重建后投影
     * @param minorUnit 账簿币种小数位
     * @return SHA-256 小写十六进制
     */
    public static String compute(TrialBalanceException exception, ProjectionSnapshot before, ProjectionSnapshot after,
        int minorUnit) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        try (JsonGenerator generator = JSON.createGenerator(ObjectWriteContext.empty(), json)) {
            generator.writeStartObject();
            generator.writeStringProperty("schema", SCHEMA);
            generator.writeStringProperty("exceptionId", String.valueOf(exception.getId()));
            generator.writeStringProperty("accountId", String.valueOf(exception.getReferenceId()));
            generator.writeStringProperty("makerId", String.valueOf(exception.getResolutionMakerId()));
            generator.writeStringProperty("checkerId", String.valueOf(exception.getResolutionCheckerId()));
            writeSnapshot(generator, "before", before, minorUnit);
            writeSnapshot(generator, "after", after, minorUnit);
            generator.writeEndObject();
        }
        return sha256(Rfc8785JsonCanonicalizer.canonicalizeObject(json.toByteArray()));
    }

    /** 写出一组投影值。 */
    static void writeSnapshot(JsonGenerator generator, String name, ProjectionSnapshot snapshot, int minorUnit) {
        generator.writeName(name);
        generator.writeStartObject();
        generator.writeStringProperty("debitTotal", amount(snapshot.getDebitTotal(), minorUnit));
        generator.writeStringProperty("creditTotal", amount(snapshot.getCreditTotal(), minorUnit));
        generator.writeStringProperty("netBalance", amount(snapshot.getNetBalance(), minorUnit));
        generator.writeStringProperty("lastJournalSequence", String.valueOf(snapshot.getLastJournalSequence()));
        generator.writeEndObject();
    }

    /**
     * 金额按币种精度输出普通十进制字符串
     * <li>被篡改的投影可能超出币种精度，此处按 HALF_UP 收敛仅用于摘要输入，不改变持久化值。</li>
     */
    static String amount(BigDecimal value, int minorUnit) {
        return value.setScale(minorUnit, RoundingMode.HALF_UP).toPlainString();
    }

    /** 计算 SHA-256 小写十六进制。 */
    static String sha256(byte[] canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new ErrorException("运行环境缺少 SHA-256", e);
        }
    }
}
