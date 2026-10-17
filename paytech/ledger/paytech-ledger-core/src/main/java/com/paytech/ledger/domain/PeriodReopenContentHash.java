package com.paytech.ledger.domain;

import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import com.paytech.ledger.core.dal.dataobject.CloseStep;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.PeriodClose;
import com.paytech.ledger.core.dal.dataobject.PeriodCloseBook;
import com.paytech.ledger.core.enums.CloseStepType;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * 重开流程摘要 {@value #SCHEMA}
 * <li>对象字段：schema, periodCloseId, periodId, closeNo, closeType, makerId, reopensCloseId, reopensCloseHash,
 * reopenReasonCode, reopenRequestRef,
 * books（按 ledgerBookId 数值升序，每项 ledgerBookId, asOfSequence,
 * reversalLines〔按行号，每项 accountId, entrySide, amount〕；原关账无结转指令的账簿 reversalLines 为空数组），
 * steps（只含步骤 1—2，按 stepNo 升序，每项 stepNo, stepType, status, resultHash）。</li>
 * <li>ID、序号与金额写字符串，金额去尾零后取普通十进制；先用 Jackson 写出 JSON，再经 RFC 8785 规范化后计算 SHA-256 小写十六进制。</li>
 * <li>发起时冻结；批准时按同一规则重算冲回计划后比对，不一致即拒绝。</li>
 */
public final class PeriodReopenContentHash {
    /** 摘要输入的版本标识，变更输入结构时必须新增版本。 */
    public static final String SCHEMA = "CODE-MOR:FINANCE:PERIOD_REOPEN:1";

    private static final JsonFactory JSON = new JsonFactory();

    private PeriodReopenContentHash() {
    }

    /**
     * 计算重开摘要
     *
     * @param context 已分配序号、冻结水位且步骤 1—2 已成功的重开上下文
     * @return SHA-256 小写十六进制
     */
    public static String compute(PeriodReopenContext context) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        try (JsonGenerator generator = JSON.createGenerator(ObjectWriteContext.empty(), json)) {
            writeHeader(generator, context.getClose(), context.getReopensClose());
            generator.writeName("books");
            generator.writeStartArray();
            for (PeriodCloseBook book : context.getCloseBooks().stream()
                .sorted(Comparator.comparing(PeriodCloseBook::getLedgerBookId)).toList()) {
                writeBook(generator, book, context.getReversalLines());
            }
            generator.writeEndArray();
            generator.writeName("steps");
            generator.writeStartArray();
            for (CloseStep step : context.getSteps().stream().filter(step -> step.getStepType() != CloseStepType.POST_REOPEN_REVERSAL)
                .sorted(Comparator.comparing(CloseStep::getStepNo)).toList()) {
                PeriodCloseContentHash.writeStep(generator, step);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
        return PeriodCloseContentHash.hex(Rfc8785JsonCanonicalizer.canonicalizeObject(json.toByteArray()));
    }

    /** 冲回计划的规范文本：按账簿主键升序、行号顺序拼接“账簿|账户|方向|金额”，用于步骤 2 结果摘要。 */
    public static String planText(Map<Long, List<InstructionLine>> reversalLines) {
        StringBuilder text = new StringBuilder("PLAN_REOPEN_REVERSAL");
        reversalLines.forEach((bookId, lines) -> lines.forEach(line -> text.append('|').append(bookId).append(':')
            .append(lineText(line))));
        return text.toString();
    }

    /** 单行规范文本“账户|方向|金额”，也用于冲回指令的输入摘要。 */
    public static String lineText(InstructionLine line) {
        return line.getAccountId() + "|" + line.getEntrySide().getCode() + "|" + amount(line);
    }

    /** 写出流程头字段并保持对象打开。 */
    static void writeHeader(JsonGenerator generator, PeriodClose close, PeriodClose reopensClose) {
        PeriodCloseContentHash.writeHeader(generator, close, SCHEMA);
        generator.writeStringProperty("reopensCloseId", String.valueOf(reopensClose.getId()));
        generator.writeStringProperty("reopensCloseHash", reopensClose.getCloseHash());
        generator.writeStringProperty("reopenReasonCode", close.getReopenReasonCode());
        generator.writeStringProperty("reopenRequestRef", close.getReopenRequestRef());
    }

    /** 写出一个账簿的水位与冲回行。 */
    static void writeBook(JsonGenerator generator, PeriodCloseBook book, Map<Long, List<InstructionLine>> reversalLines) {
        generator.writeStartObject();
        generator.writeStringProperty("ledgerBookId", String.valueOf(book.getLedgerBookId()));
        generator.writeStringProperty("asOfSequence", String.valueOf(book.getAsOfSequence()));
        generator.writeName("reversalLines");
        generator.writeStartArray();
        for (InstructionLine line : reversalLines.getOrDefault(book.getLedgerBookId(), List.of())) {
            generator.writeStartObject();
            generator.writeStringProperty("accountId", String.valueOf(line.getAccountId()));
            generator.writeStringProperty("entrySide", line.getEntrySide().getCode());
            generator.writeStringProperty("amount", amount(line));
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    /** 金额去尾零后的普通十进制文本。 */
    static String amount(InstructionLine line) {
        return line.getAmount().stripTrailingZeros().toPlainString();
    }
}
