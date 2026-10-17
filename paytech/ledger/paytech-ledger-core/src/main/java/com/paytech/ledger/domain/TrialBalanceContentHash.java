package com.paytech.ledger.domain;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceLine;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceRun;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * 试算运行内容摘要 {@value #SCHEMA}
 * <li>对象字段：schema, ledgerBookId, periodId, runNo, asOfSequence, status, totalDebit, totalCredit,
 * lines（按 chartAccountId 数值升序，每项 chartAccountId, openingBalance, periodDebit, periodCredit, closingBalance），
 * exceptions（按 exceptionType、referenceType、referenceId 数值升序，每项 exceptionType, referenceType, referenceId,
 * differenceAmount）。</li>
 * <li>ID 写字符串，金额按 minorUnit 补齐后写字符串；不包含异常状态与解决字段。</li>
 * <li>先用 Jackson 写出 JSON，再经 RFC 8785 规范化后计算 SHA-256 小写十六进制。</li>
 */
public final class TrialBalanceContentHash {
    /** 摘要输入的版本标识，变更输入结构时必须新增版本。 */
    public static final String SCHEMA = "CODE-MOR:FINANCE:TRIAL_BALANCE:1";

    private static final JsonFactory JSON = new JsonFactory();

    private TrialBalanceContentHash() {
    }

    /**
     * 计算试算运行内容摘要
     *
     * @param run 已填好账簿、期间、序号、水位、状态与借贷合计的运行
     * @param lines 试算行，顺序不影响结果
     * @param exceptions 异常发现事实，顺序不影响结果
     * @param minorUnit 账簿币种小数位
     * @return SHA-256 小写十六进制
     */
    public static String compute(TrialBalanceRun run, List<TrialBalanceLine> lines,
        List<TrialBalanceException> exceptions, int minorUnit) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        try (JsonGenerator generator = JSON.createGenerator(ObjectWriteContext.empty(), json)) {
            writeHeader(generator, run, minorUnit);
            generator.writeName("lines");
            generator.writeStartArray();
            for (TrialBalanceLine line : sortedLines(lines)) {
                writeLine(generator, line, minorUnit);
            }
            generator.writeEndArray();
            generator.writeName("exceptions");
            generator.writeStartArray();
            for (TrialBalanceException exception : sortedExceptions(exceptions)) {
                writeException(generator, exception, minorUnit);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
        return sha256(Rfc8785JsonCanonicalizer.canonicalizeObject(json.toByteArray()));
    }

    /** 写出运行头字段并保持对象打开。 */
    static void writeHeader(JsonGenerator generator, TrialBalanceRun run, int minorUnit) {
        generator.writeStartObject();
        generator.writeStringProperty("schema", SCHEMA);
        generator.writeStringProperty("ledgerBookId", String.valueOf(run.getLedgerBookId()));
        generator.writeStringProperty("periodId", String.valueOf(run.getPeriodId()));
        generator.writeStringProperty("runNo", String.valueOf(run.getRunNo()));
        generator.writeStringProperty("asOfSequence", String.valueOf(run.getAsOfSequence()));
        generator.writeStringProperty("status", run.getStatus().getCode());
        generator.writeStringProperty("totalDebit", amount(run.getTotalDebit(), minorUnit));
        generator.writeStringProperty("totalCredit", amount(run.getTotalCredit(), minorUnit));
    }

    /** 写出一条试算行。 */
    static void writeLine(JsonGenerator generator, TrialBalanceLine line, int minorUnit) {
        generator.writeStartObject();
        generator.writeStringProperty("chartAccountId", String.valueOf(line.getChartAccountId()));
        generator.writeStringProperty("openingBalance", amount(line.getOpeningBalance(), minorUnit));
        generator.writeStringProperty("periodDebit", amount(line.getPeriodDebit(), minorUnit));
        generator.writeStringProperty("periodCredit", amount(line.getPeriodCredit(), minorUnit));
        generator.writeStringProperty("closingBalance", amount(line.getClosingBalance(), minorUnit));
        generator.writeEndObject();
    }

    /** 写出一条异常发现事实。 */
    static void writeException(JsonGenerator generator, TrialBalanceException exception, int minorUnit) {
        generator.writeStartObject();
        generator.writeStringProperty("exceptionType", exception.getExceptionType().getCode());
        generator.writeStringProperty("referenceType", exception.getReferenceType().getCode());
        generator.writeStringProperty("referenceId", String.valueOf(exception.getReferenceId()));
        generator.writeStringProperty("differenceAmount", amount(exception.getDifferenceAmount(), minorUnit));
        generator.writeEndObject();
    }

    /** 按科目主键升序复制。 */
    static List<TrialBalanceLine> sortedLines(List<TrialBalanceLine> lines) {
        return lines.stream().sorted(Comparator.comparing(TrialBalanceLine::getChartAccountId)).toList();
    }

    /** 按类型、引用类型、引用主键升序复制。 */
    static List<TrialBalanceException> sortedExceptions(List<TrialBalanceException> exceptions) {
        return exceptions.stream()
            .sorted(Comparator.comparing((TrialBalanceException e) -> e.getExceptionType().getCode())
                .thenComparing(e -> e.getReferenceType().getCode()).thenComparing(TrialBalanceException::getReferenceId))
            .toList();
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
