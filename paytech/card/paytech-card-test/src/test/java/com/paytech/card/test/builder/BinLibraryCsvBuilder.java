package com.paytech.card.test.builder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.paytech.card.domain.binlibrary.BinLibraryCsv;

/**
 * 平台标准 BIN 库 CSV 的测试构造：默认带标准表头、LF 换行；负例可替换表头、写入原始行或非法字节。不修复用例刻意写入的非法值。
 */
public final class BinLibraryCsvBuilder {
    private String header = BinLibraryCsv.HEADER;
    private final List<String> lines = new ArrayList<>();
    private String lineSeparator = "\n";
    private boolean bom;

    private BinLibraryCsvBuilder() {
    }

    /** 标准表头、没有数据行。 */
    public static BinLibraryCsvBuilder template() {
        return new BinLibraryCsvBuilder();
    }

    /** 替换表头（负例）。 */
    public BinLibraryCsvBuilder header(String header) {
        this.header = header;
        return this;
    }

    /** 使用 CRLF 换行。 */
    public BinLibraryCsvBuilder crlf() {
        this.lineSeparator = "\r\n";
        return this;
    }

    /** 文件开头写入 UTF-8 BOM。 */
    public BinLibraryCsvBuilder bom() {
        this.bom = true;
        return this;
    }

    /** 追加一行 7 列数据（null 写为空列）。 */
    public BinLibraryCsvBuilder row(String binPrefix, String cardBrand, String fundingType, String cardLevel,
        String cardProduct, String issuerCountry, String issuerName) {
        return raw(String.join(",", nullToEmpty(binPrefix), nullToEmpty(cardBrand), nullToEmpty(fundingType),
            nullToEmpty(cardLevel), nullToEmpty(cardProduct), nullToEmpty(issuerCountry), nullToEmpty(issuerName)));
    }

    /** 追加一行原始文本（负例：列数、引号、空行）。 */
    public BinLibraryCsvBuilder raw(String line) {
        lines.add(line);
        return this;
    }

    /** UTF-8 字节，末尾带一个换行。 */
    public byte[] build() {
        StringBuilder text = new StringBuilder(header);
        for (String line : lines) {
            text.append(lineSeparator).append(line);
        }
        text.append(lineSeparator);
        byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);
        if (!bom) {
            return body;
        }
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte)0xEF;
        withBom[1] = (byte)0xBB;
        withBom[2] = (byte)0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return withBom;
    }

    /** null 写为空列。 */
    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
