package com.paytech.finance.accounting.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.paytech.finance.accounting.core.dal.dataobject.CloseStep;
import com.paytech.finance.accounting.core.dal.dataobject.PeriodClose;
import com.paytech.finance.accounting.core.dal.dataobject.PeriodCloseBook;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * 关账流程摘要 {@value #SCHEMA}
 * <li>对象字段：schema, periodCloseId, periodId, closeNo, closeType, makerId,
 * books（按 ledgerBookId 数值升序，每项 ledgerBookId, asOfSequence, finalTrialRunId, finalTrialContentHash,
 * postingInstructionId；无结转指令时 postingInstructionId 为 null），
 * steps（按 stepNo 升序，每项 stepNo, stepType, status, resultHash）。</li>
 * <li>ID 与序号写字符串；先用 Jackson 写出 JSON，再经 RFC 8785 规范化后计算 SHA-256 小写十六进制。</li>
 * <li>本期没有异常解决集合：冻结前步骤 5 已保证不存在未解决异常，解决事实已冻结在最终试算之前的异常行中。</li>
 */
public final class PeriodCloseContentHash {
    /** 摘要输入的版本标识，变更输入结构时必须新增版本。 */
    public static final String SCHEMA = "CODE-MOR:FINANCE:PERIOD_CLOSE:1";

    private static final JsonFactory JSON = new JsonFactory();

    private PeriodCloseContentHash() {
    }

    /**
     * 计算关账摘要
     *
     * @param close 流程根
     * @param books 已 FINALIZED 的账簿证据
     * @param steps 全部步骤
     * @param finalTrialHashes 账簿主键 → 最终试算内容摘要
     * @return SHA-256 小写十六进制
     */
    public static String compute(PeriodClose close, List<PeriodCloseBook> books, List<CloseStep> steps,
        Map<Long, String> finalTrialHashes) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        try (JsonGenerator generator = JSON.createGenerator(ObjectWriteContext.empty(), json)) {
            writeHeader(generator, close);
            generator.writeName("books");
            generator.writeStartArray();
            for (PeriodCloseBook book : books.stream().sorted(Comparator.comparing(PeriodCloseBook::getLedgerBookId)).toList()) {
                writeBook(generator, book, finalTrialHashes.get(book.getLedgerBookId()));
            }
            generator.writeEndArray();
            generator.writeName("steps");
            generator.writeStartArray();
            for (CloseStep step : steps.stream().sorted(Comparator.comparing(CloseStep::getStepNo)).toList()) {
                writeStep(generator, step);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
        return hex(Rfc8785JsonCanonicalizer.canonicalizeObject(json.toByteArray()));
    }

    /** 对步骤结果摘要文本或结转行文本计算 SHA-256 小写十六进制。 */
    public static String sha256(String text) {
        return hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** 写出流程头字段并保持对象打开。 */
    static void writeHeader(JsonGenerator generator, PeriodClose close) {
        writeHeader(generator, close, SCHEMA);
    }

    /** 按给定摘要版本写出流程头字段并保持对象打开；重开摘要复用同一组头字段。 */
    static void writeHeader(JsonGenerator generator, PeriodClose close, String schema) {
        generator.writeStartObject();
        generator.writeStringProperty("schema", schema);
        generator.writeStringProperty("periodCloseId", String.valueOf(close.getId()));
        generator.writeStringProperty("periodId", String.valueOf(close.getPeriodId()));
        generator.writeStringProperty("closeNo", String.valueOf(close.getCloseNo()));
        generator.writeStringProperty("closeType", close.getCloseType().getCode());
        generator.writeStringProperty("makerId", String.valueOf(close.getMakerId()));
    }

    /** 写出一个账簿证据。 */
    static void writeBook(JsonGenerator generator, PeriodCloseBook book, String finalTrialHash) {
        generator.writeStartObject();
        generator.writeStringProperty("ledgerBookId", String.valueOf(book.getLedgerBookId()));
        generator.writeStringProperty("asOfSequence", String.valueOf(book.getAsOfSequence()));
        generator.writeStringProperty("finalTrialRunId", String.valueOf(book.getFinalTrialRunId()));
        generator.writeStringProperty("finalTrialContentHash", finalTrialHash);
        generator.writeStringProperty("postingInstructionId",
            book.getPostingInstructionId() == null ? null : String.valueOf(book.getPostingInstructionId()));
        generator.writeEndObject();
    }

    /** 写出一个步骤。 */
    static void writeStep(JsonGenerator generator, CloseStep step) {
        generator.writeStartObject();
        generator.writeStringProperty("stepNo", String.valueOf(step.getStepNo()));
        generator.writeStringProperty("stepType", step.getStepType().getCode());
        generator.writeStringProperty("status", step.getStatus().getCode());
        generator.writeStringProperty("resultHash", step.getResultHash());
        generator.writeEndObject();
    }

    /** 计算 SHA-256 小写十六进制。 */
    static String hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new ErrorException("运行环境缺少 SHA-256", e);
        }
    }
}
