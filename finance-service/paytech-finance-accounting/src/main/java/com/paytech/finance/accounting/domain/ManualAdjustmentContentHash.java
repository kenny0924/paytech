package com.paytech.finance.accounting.domain;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.ManualAdjustment;
import com.paytech.finance.accounting.core.dal.dataobject.ManualAdjustmentLine;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * 人工调整内容摘要 {@value #SCHEMA}
 * <li>覆盖调整头与按行号排序的全部明细；ID 和金额都以字符串表达，金额按币种精度补齐。</li>
 * <li>先写出 JSON，再经 RFC 8785 规范化后计算 SHA-256 小写十六进制。</li>
 */
public final class ManualAdjustmentContentHash {
    /** 摘要输入的版本标识，变更输入结构时必须新增版本。 */
    public static final String SCHEMA = "CODE-MOR:FINANCE:MANUAL_ADJUSTMENT:1";

    private static final JsonFactory JSON = new JsonFactory();

    private ManualAdjustmentContentHash() {
    }

    /**
     * 计算调整内容摘要
     *
     * @param adjustment 已填好单号、账簿、期间、币种、原因、附件、合计和 Maker 的调整头
     * @param lines 调整明细，顺序不影响结果
     * @param minorUnit 币种小数位，用于规范金额字符串
     * @return SHA-256 小写十六进制
     */
    public static String compute(ManualAdjustment adjustment, List<ManualAdjustmentLine> lines, int minorUnit) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        try (JsonGenerator generator = JSON.createGenerator(ObjectWriteContext.empty(), json)) {
            writeHeader(generator, adjustment, minorUnit);
            generator.writeName("lines");
            generator.writeStartArray();
            for (ManualAdjustmentLine line : sorted(lines)) {
                writeLine(generator, line, minorUnit);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
        return sha256(Rfc8785JsonCanonicalizer.canonicalizeObject(json.toByteArray()));
    }

    /** 写出调整头字段并保持对象处于打开状态。 */
    static void writeHeader(JsonGenerator generator, ManualAdjustment adjustment, int minorUnit) {
        generator.writeStartObject();
        generator.writeStringProperty("schema", SCHEMA);
        generator.writeStringProperty("adjustmentNo", adjustment.getAdjustmentNo());
        generator.writeStringProperty("ledgerBookId", String.valueOf(adjustment.getLedgerBookId()));
        generator.writeStringProperty("accountingPeriodId", String.valueOf(adjustment.getAccountingPeriodId()));
        generator.writeStringProperty("currency", adjustment.getCurrency());
        generator.writeStringProperty("reasonCode", adjustment.getReasonCode());
        generator.writeStringProperty("fileId", adjustment.getFileId() == null ? null : adjustment.getFileId().toString());
        generator.writeStringProperty("fileContentHash", adjustment.getFileContentHash());
        generator.writeStringProperty("makerId", String.valueOf(adjustment.getMakerId()));
        generator.writeStringProperty("totalDebit", amount(adjustment.getTotalDebit(), minorUnit));
        generator.writeStringProperty("totalCredit", amount(adjustment.getTotalCredit(), minorUnit));
    }

    /** 写出一条明细。 */
    static void writeLine(JsonGenerator generator, ManualAdjustmentLine line, int minorUnit) {
        generator.writeStartObject();
        generator.writeNumberProperty("lineNo", line.getLineNo());
        generator.writeStringProperty("accountId", String.valueOf(line.getAccountId()));
        generator.writeStringProperty("entrySide", line.getEntrySide().getCode());
        generator.writeStringProperty("amount", amount(line.getAmount(), minorUnit));
        generator.writeStringProperty("lineDescription", line.getLineDescription());
        generator.writeEndObject();
    }

    /** 按行号升序复制明细，不改变调用方列表。 */
    static List<ManualAdjustmentLine> sorted(List<ManualAdjustmentLine> lines) {
        return lines.stream().sorted(Comparator.comparing(ManualAdjustmentLine::getLineNo)).toList();
    }

    /** 金额按币种精度输出普通十进制字符串；调用方已保证没有超精度。 */
    static String amount(BigDecimal value, int minorUnit) {
        return value.setScale(minorUnit).toPlainString();
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
