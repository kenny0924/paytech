package com.paytech.ledger.domain;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * 投影重建解决摘要 {@value #SCHEMA}
 * <li>对象字段：schema, exceptionId, accountId, makerId, checkerId, journalTotals；journalTotals 含 debitTotal,
 * creditTotal, netBalance, lastJournalSequence。</li>
 * <li>第 11b 步起 ledger 不再保存余额投影（design/04 §4），批准时只冻结账户按分录聚合的四个值；Owner 余额的重建由 account 服务执行。</li>
 * <li>ID 与序号写字符串，金额按 minorUnit 补齐后写字符串。</li>
 * <li>先用 Jackson 写出 JSON，再经 RFC 8785 规范化后计算 SHA-256 小写十六进制。</li>
 */
public final class ProjectionRebuildContentHash {
    /** 摘要输入的版本标识，变更输入结构时必须新增版本。 */
    public static final String SCHEMA = "CODE-MOR:LEDGER:PROJECTION_REBUILD:2";

    private static final JsonFactory JSON = new JsonFactory();

    private ProjectionRebuildContentHash() {
    }

    /**
     * 计算投影重建解决摘要
     *
     * @param exception 已填好引用账户、编制人与复核人的异常
     * @param journalTotals 批准时账户按全部分录聚合的四个值
     * @param minorUnit 账簿币种小数位
     * @return SHA-256 小写十六进制
     */
    public static String compute(TrialBalanceException exception, ProjectionSnapshot journalTotals, int minorUnit) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        try (JsonGenerator generator = JSON.createGenerator(ObjectWriteContext.empty(), json)) {
            generator.writeStartObject();
            generator.writeStringProperty("schema", SCHEMA);
            generator.writeStringProperty("exceptionId", String.valueOf(exception.getId()));
            generator.writeStringProperty("accountId", String.valueOf(exception.getReferenceId()));
            generator.writeStringProperty("makerId", String.valueOf(exception.getResolutionMakerId()));
            generator.writeStringProperty("checkerId", String.valueOf(exception.getResolutionCheckerId()));
            writeSnapshot(generator, "journalTotals", journalTotals, minorUnit);
            generator.writeEndObject();
        }
        return sha256(Rfc8785JsonCanonicalizer.canonicalizeObject(json.toByteArray()));
    }

    /** 写出一组余额值。 */
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
     * <li>分录金额已由过账内核保证不超精度，此处 HALF_UP 只是防御性收敛，不改变持久化值。</li>
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
