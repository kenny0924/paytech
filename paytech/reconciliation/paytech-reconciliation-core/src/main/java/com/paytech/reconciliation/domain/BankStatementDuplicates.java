package com.paytech.reconciliation.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 银行对账单接收中记下的重复交易号原因（2026-10-20 主会话决定：含重复交易号的对账单完成时整单拒绝）
 * <li>交易号受 uk(cash_account_id, bank_transaction_ref) 约束，重复的行无法入库；追加时把「第 n 行：原因」记在 RECEIVING 对账单的
 * reject_reason 中（以「；」分隔），完成导入时还原为逐行拒绝原因。按行号去重，同一段重发不重复记。</li>
 * <li>纯文本处理，不访问数据库。</li>
 */
public final class BankStatementDuplicates {
    /** reject_reason 列上限。 */
    static final int LIMIT = 1000;

    /** 条目分隔符。 */
    static final String SEPARATOR = "；";

    /** 条目格式。 */
    static final Pattern ENTRY = Pattern.compile("第 (\\d+) 行：(.+)");

    private BankStatementDuplicates() {
    }

    /** 某行是否已记下。 */
    public static boolean noted(String text, int lineNo) {
        return text != null && (text.startsWith(prefix(lineNo)) || text.contains(SEPARATOR + prefix(lineNo)));
    }

    /**
     * 追加一条行原因；已记下的行不重复，超过列上限时不再追加文本（行数仍由调用方计入）
     *
     * @param text 已记下的原因，可为空
     * @param lineNo 行号
     * @param reason 原因
     * @return 新文本
     */
    public static String append(String text, int lineNo, String reason) {
        String entry = prefix(lineNo) + reason;
        if (noted(text, lineNo)) {
            return text;
        }
        String next = text == null ? entry : text + SEPARATOR + entry;
        return next.length() <= LIMIT ? next : text;
    }

    /** 还原为按行号升序的逐行拒绝原因。 */
    public static List<LineRejection> rejections(String text) {
        List<LineRejection> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String item : text.split(SEPARATOR)) {
            Matcher matcher = ENTRY.matcher(item);
            if (matcher.matches()) {
                result.add(new LineRejection(Integer.valueOf(matcher.group(1)), matcher.group(2)));
            }
        }
        result.sort(Comparator.comparing(LineRejection::getLineNo));
        return result;
    }

    /** 行前缀。 */
    static String prefix(int lineNo) {
        return "第 " + lineNo + " 行：";
    }
}
