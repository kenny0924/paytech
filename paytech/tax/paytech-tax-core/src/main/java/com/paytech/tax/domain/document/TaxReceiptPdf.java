package com.paytech.tax.domain.document;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 把票面快照渲染为单页 PDF（PDF 1.4，标准 Helvetica 字体，无外部依赖；technique 9.3）
 * <li>仓库与本地 Maven 库都没有 PDF 库，按 ISO 32000 最小结构直接输出：Catalog、Pages、Page、Font、Content 与交叉引用表。</li>
 * <li>标准 14 字体只覆盖 WinAnsi：非 ASCII 字符输出为 "?"（阿拉伯语等双语票面需嵌入字体，随版式迁入后实现）。内容确定，同一快照字节相同。</li>
 * <li>纯函数；不含消费者资料（快照本身不含）。</li>
 */
public final class TaxReceiptPdf {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 法定文字键 → 英文正文；未登记的键原样输出。 */
    private static final Map<String, String> LEGAL_TEXT = Map.of(
        "tax.receipt.legal.noBuyerData.AE", "Simplified tax invoice issued without buyer details.",
        "tax.receipt.legal.noBuyerData.SA", "Simplified tax invoice issued without buyer details.",
        "tax.receipt.legal.noBuyerData.EG", "Receipt issued without buyer details.");

    /** 纯静态渲染。 */
    private TaxReceiptPdf() {
    }

    /**
     * 渲染票面
     *
     * @param contentJson document_content_json
     * @return PDF 字节
     */
    public static byte[] render(String contentJson) {
        JsonNode content = JSON.readTree(contentJson);
        return pdf(textLines(content));
    }

    /** 票面文字行。 */
    static List<String> textLines(JsonNode content) {
        List<String> lines = new ArrayList<>();
        lines.add("Simplified Tax Invoice / Receipt");
        lines.add("No. " + content.path("documentNumber").asString() + "    Date " + content.path("issueDate").asString());
        lines.add("Seller: " + content.path("seller").path("name").asString());
        lines.add("Tax registration no.: " + content.path("seller").path("taxRegistrationNo").asString() + " ("
            + content.path("seller").path("jurisdictionCode").asString() + ")");
        lines.add("Order reference: " + content.path("orderReference").asString());
        lines.add("");
        lines.add("Item | Qty | Net | VAT rate | VAT | Total (" + content.path("currency").asString() + ")");
        for (JsonNode line : content.path("lines")) {
            lines.add(line.path("description").asString() + " | " + line.path("quantity").asString() + " | "
                + line.path("netAmount").asString() + " | " + percent(line.path("rate")) + " | "
                + line.path("taxAmount").asString() + " | " + line.path("grossAmount").asString());
        }
        JsonNode totals = content.path("totals");
        lines.add("");
        lines.add("Net total: " + totals.path("netAmount").asString());
        lines.add("VAT total: " + totals.path("taxAmount").asString());
        lines.add("Amount due: " + totals.path("grossAmount").asString() + " " + content.path("currency").asString());
        lines.add("");
        for (JsonNode key : content.path("legalTextKeys")) {
            lines.add(LEGAL_TEXT.getOrDefault(key.asString(), key.asString()));
        }
        return lines;
    }

    /** 税率小数转百分比文字；无税率为 "-"。 */
    static String percent(JsonNode rate) {
        if (rate.isNull() || rate.isMissingNode()) {
            return "-";
        }
        return new BigDecimal(rate.asString()).movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }

    /** 组装 PDF 对象与交叉引用表。 */
    static byte[] pdf(List<String> lines) {
        byte[] stream = contentStream(lines).getBytes(StandardCharsets.US_ASCII);
        List<String> objects = List.of("<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
            "<< /Length " + stream.length + " >>\nstream\n" + new String(stream, StandardCharsets.US_ASCII) + "\nendstream");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(out.size());
            write(out, (index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        offsets.forEach(offset -> table.append(String.format("%010d 00000 n \n", offset)));
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref)
            .append("\n%%EOF\n");
        write(out, table.toString());
        return out.toByteArray();
    }

    /** 页面内容流：左上起每行 14pt 行距。 */
    static String contentStream(List<String> lines) {
        StringBuilder stream = new StringBuilder("BT\n/F1 11 Tf\n14 TL\n50 790 Td\n");
        for (String line : lines) {
            stream.append('(').append(escape(line)).append(") Tj T*\n");
        }
        return stream.append("ET").toString();
    }

    /** PDF 字符串转义；非 ASCII 可打印字符替换为 "?"。 */
    static String escape(String text) {
        StringBuilder escaped = new StringBuilder();
        for (char character : text.toCharArray()) {
            if (character == '(' || character == ')' || character == '\\') {
                escaped.append('\\').append(character);
            } else if (character < 0x20 || character > 0x7E) {
                escaped.append('?');
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }

    /** ASCII 写入。 */
    static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
    }
}
