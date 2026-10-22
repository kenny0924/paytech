package com.paytech.card.domain.binlibrary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.paytech.card.api.enums.BinImportErrorCode;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.ErrorException;

/**
 * BIN 库平台标准 CSV（技术方案 §3.2）的两遍流式读取：第一遍逐行校验并计算 SHA-256，第二遍把校验通过的行分批交给调用方写入。
 * <li>只按物理行处理：一行一条记录，引号内不允许换行；UTF-8（开头 BOM 去除），LF 或 CRLF，逐行严格解码，不得含 NUL。</li>
 * <li>错误说明不含出错值；值摘录中超过 8 位的连续数字一律不回显，任何结果、异常与日志都不出现完整卡号。</li>
 * <li>纯内存计算，不访问数据库或外部服务；输入流由调用方打开与关闭。</li>
 */
public final class BinLibraryCsv {
    /** 第 1 行必须逐字等于的表头。 */
    public static final String HEADER = "bin_prefix,card_brand,funding_type,card_level,card_product,issuer_country,issuer_name";

    /** 数据行上限。 */
    public static final int MAX_DATA_ROWS = 1_000_000;

    /** 文件字节上限 50 MiB（与 Platform 扫描策略 CARD_BIN_CSV 一致）。 */
    public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    /** 每个版本最多记录的错误明细条数。 */
    public static final int MAX_RECORDED_ERRORS = 200;

    /** 单行字节上限；超过即判该行格式错误，不缓存剩余字节。 */
    static final int MAX_LINE_BYTES = 4096;

    static final String COLUMN_BIN_PREFIX = "bin_prefix";
    static final String COLUMN_CARD_BRAND = "card_brand";
    static final String COLUMN_FUNDING_TYPE = "funding_type";
    static final String COLUMN_CARD_LEVEL = "card_level";
    static final String COLUMN_CARD_PRODUCT = "card_product";
    static final String COLUMN_ISSUER_COUNTRY = "issuer_country";
    static final String COLUMN_ISSUER_NAME = "issuer_name";

    private static final int COLUMN_COUNT = 7;
    private static final int MAX_ISSUER_NAME_LENGTH = 128;
    private static final int MAX_EXCERPT_LENGTH = 64;
    private static final Pattern BIN_PREFIX = Pattern.compile("[0-9]{6,8}");
    private static final Pattern CARD_LEVEL = Pattern.compile("[A-Z0-9_]{1,32}");
    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");
    private static final Pattern LONG_DIGITS = Pattern.compile("[0-9]{9,}");
    private static final byte[] BOM = {(byte)0xEF, (byte)0xBB, (byte)0xBF};

    private BinLibraryCsv() {
    }

    /**
     * 第一遍：读完整个文件，校验表头、行数与每一行
     *
     * @param in 文件字节流
     * @param regions 国家代码 → 区域代码
     * @return 校验结果；表头不符、超过行数或字节上限时为文件级失败
     * @throws IOException 读取失败（由调用方按可重试处理）
     */
    public static BinLibraryValidation validate(InputStream in, Map<String, String> regions) throws IOException {
        LineSource source = new LineSource(in);
        String headerFailure = checkHeader(source.next());
        if (headerFailure != null) {
            return BinLibraryValidation.fileFailure(headerFailure);
        }
        ValidationState state = new ValidationState(regions);
        for (Line line = source.next(); line != null; line = source.next()) {
            if (source.bytesRead() > MAX_FILE_BYTES) {
                return BinLibraryValidation.fileFailure("文件超过 50 MiB 上限");
            }
            state.accept(line);
            if (state.dataRows > MAX_DATA_ROWS) {
                return BinLibraryValidation.fileFailure("数据行超过 " + MAX_DATA_ROWS + " 行上限");
            }
        }
        return new BinLibraryValidation(source.contentHash(), null, state.dataRows, state.errorLines.cardinality(),
            List.copyOf(state.errors));
    }

    /**
     * 第二遍：按行解析已通过校验的文件，每 batchSize 行交给调用方一次
     *
     * @param in 文件字节流
     * @param regions 国家代码 → 区域代码
     * @param batchSize 每批行数
     * @param batches 接收每一批号段的调用方（如写入一个短事务）
     * @return 实际字节的 SHA-256 小写十六进制，由调用方与冻结值核对
     * @throws IOException 读取失败
     */
    public static String readRanges(InputStream in, Map<String, String> regions, int batchSize,
        Consumer<List<BinRangeRow>> batches) throws IOException {
        LineSource source = new LineSource(in);
        if (checkHeader(source.next()) != null) {
            throw new ErrorException("BIN 库文件第二遍读取时表头不符");
        }
        List<BinRangeRow> batch = new ArrayList<>(batchSize);
        for (Line line = source.next(); line != null; line = source.next()) {
            if (line.text() != null && line.text().isEmpty()) {
                continue;
            }
            batch.add(requireRow(line, regions));
            if (batch.size() == batchSize) {
                batches.accept(List.copyOf(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            batches.accept(List.copyOf(batch));
        }
        return source.contentHash();
    }

    /** 表头核对：返回文件级失败原因，通过时为 null。 */
    static String checkHeader(Line header) {
        if (header == null) {
            return "文件为空，缺少表头";
        }
        if (!HEADER.equals(header.text())) {
            return "表头不符：第 1 行必须为 " + HEADER;
        }
        return null;
    }

    /** 第二遍解析一行；任何错误都说明文件与第一遍不一致，按系统错误处理。 */
    static BinRangeRow requireRow(Line line, Map<String, String> regions) {
        List<BinImportRowError> errors = new ArrayList<>();
        BinRangeRow row = parseRow(line, regions, errors);
        if (row == null) {
            throw new ErrorException("BIN 库文件第二遍读取发现未通过校验的行: 第 " + line.lineNo() + " 行");
        }
        return row;
    }

    /**
     * 解析并校验一行数据
     *
     * @param line 物理行
     * @param regions 国家代码 → 区域代码
     * @param errors 收集本行全部错误
     * @return 校验通过的号段；有错误时为 null
     */
    static BinRangeRow parseRow(Line line, Map<String, String> regions, List<BinImportRowError> errors) {
        if (line.encodingInvalid()) {
            errors.add(rowError(line, BinImportErrorCode.ENCODING_INVALID, "该行不是合法 UTF-8、含 NUL 或超过 4096 字节"));
            return null;
        }
        List<String> fields = split(line.text());
        if (fields == null || fields.size() != COLUMN_COUNT) {
            errors.add(rowError(line, BinImportErrorCode.COLUMN_COUNT_MISMATCH, "列数必须为 7，且引号须成对闭合"));
            return null;
        }
        int before = errors.size();
        String binPrefix = required(line, fields.get(0), COLUMN_BIN_PREFIX, BIN_PREFIX, "必须为 6 至 8 位数字", errors);
        CardBrand cardBrand = requiredCode(line, fields.get(1), COLUMN_CARD_BRAND, CardBrand.class, errors);
        CardFundingType fundingType = optionalCode(line, fields.get(2), COLUMN_FUNDING_TYPE, CardFundingType.class, errors);
        String cardLevel = optional(line, fields.get(3), COLUMN_CARD_LEVEL, CARD_LEVEL, "必须为 1 至 32 位大写字母、数字或下划线",
            errors);
        CardProduct cardProduct = optionalCode(line, fields.get(4), COLUMN_CARD_PRODUCT, CardProduct.class, errors);
        String country = required(line, fields.get(5), COLUMN_ISSUER_COUNTRY, COUNTRY, "必须为两位大写字母国家代码", errors);
        String region = country == null ? null : region(line, country, regions, errors);
        String issuerName = issuerName(line, fields.get(6), errors);
        if (errors.size() > before) {
            return null;
        }
        return new BinRangeRow(binPrefix, cardBrand, fundingType, cardLevel, cardProduct, country, region, issuerName);
    }

    /** 必填且匹配格式；返回 null 表示已记错。 */
    static String required(Line line, String value, String column, Pattern format, String formatMessage,
        List<BinImportRowError> errors) {
        if (value.isEmpty()) {
            errors.add(columnError(line, column, BinImportErrorCode.REQUIRED_MISSING, column + " 必填", value));
            return null;
        }
        return formatted(line, value, column, format, formatMessage, errors);
    }

    /** 可空；有值时须匹配格式。 */
    static String optional(Line line, String value, String column, Pattern format, String formatMessage,
        List<BinImportRowError> errors) {
        return value.isEmpty() ? null : formatted(line, value, column, format, formatMessage, errors);
    }

    /** 格式校验；不符记 INVALID_FORMAT。 */
    static String formatted(Line line, String value, String column, Pattern format, String formatMessage,
        List<BinImportRowError> errors) {
        if (format.matcher(value).matches()) {
            return value;
        }
        errors.add(columnError(line, column, BinImportErrorCode.INVALID_FORMAT, column + " " + formatMessage, value));
        return null;
    }

    /** 必填的闭合代码，大小写敏感。 */
    static <E extends Enum<E> & CodeEnum> E requiredCode(Line line, String value, String column, Class<E> type,
        List<BinImportRowError> errors) {
        if (value.isEmpty()) {
            errors.add(columnError(line, column, BinImportErrorCode.REQUIRED_MISSING, column + " 必填", value));
            return null;
        }
        return optionalCode(line, value, column, type, errors);
    }

    /** 可空的闭合代码，大小写敏感；不在清单内记 INVALID_VALUE。 */
    static <E extends Enum<E> & CodeEnum> E optionalCode(Line line, String value, String column, Class<E> type,
        List<BinImportRowError> errors) {
        if (value.isEmpty()) {
            return null;
        }
        E code = Arrays.stream(type.getEnumConstants()).filter(candidate -> candidate.getCode().equals(value)).findFirst()
            .orElse(null);
        if (code == null) {
            String allowed = String.join("、", Arrays.stream(type.getEnumConstants()).map(CodeEnum::getCode).toList());
            errors.add(columnError(line, column, BinImportErrorCode.INVALID_VALUE, column + " 只允许 " + allowed, value));
        }
        return code;
    }

    /** 由发卡国推导区域；映射中没有该国记 COUNTRY_REGION_MISSING。 */
    static String region(Line line, String country, Map<String, String> regions, List<BinImportRowError> errors) {
        String region = regions.get(country);
        if (region == null) {
            errors.add(columnError(line, COLUMN_ISSUER_COUNTRY, BinImportErrorCode.COUNTRY_REGION_MISSING,
                "发卡国 " + country + " 未登记区域", country));
        }
        return region;
    }

    /** 发卡行名称：去除首尾空白，空为 null，不超过 128 字符。 */
    static String issuerName(Line line, String value, List<BinImportRowError> errors) {
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_ISSUER_NAME_LENGTH) {
            errors.add(columnError(line, COLUMN_ISSUER_NAME, BinImportErrorCode.INVALID_FORMAT, "issuer_name 不能超过 128 字符",
                trimmed));
            return null;
        }
        return trimmed;
    }

    /**
     * 按 RFC 4180 拆分一行：逗号分隔，双引号包围的字段内 "" 表示一个引号
     *
     * @param line 不含换行的一行
     * @return 字段列表；引号不成对或出现在未加引号的字段中时为 null
     */
    static List<String> split(String line) {
        List<String> fields = new ArrayList<>(COLUMN_COUNT);
        int position = 0;
        while (true) {
            StringBuilder field = new StringBuilder();
            position = line.startsWith("\"", position) ? quoted(line, position + 1, field) : plain(line, position, field);
            if (position < 0) {
                return null;
            }
            fields.add(field.toString());
            if (position == line.length()) {
                return fields;
            }
            position++;
        }
    }

    /** 读取引号字段，返回其后逗号或行尾的位置；格式错误返回 -1。 */
    static int quoted(String line, int start, StringBuilder field) {
        int position = start;
        while (position < line.length()) {
            char current = line.charAt(position);
            if (current != '"') {
                field.append(current);
                position++;
                continue;
            }
            if (line.startsWith("\"\"", position)) {
                field.append('"');
                position += 2;
                continue;
            }
            int next = position + 1;
            return next == line.length() || line.charAt(next) == ',' ? next : -1;
        }
        return -1;
    }

    /** 读取未加引号的字段，返回其后逗号或行尾的位置；字段内出现引号返回 -1。 */
    static int plain(String line, int start, StringBuilder field) {
        int end = line.indexOf(',', start);
        String value = end < 0 ? line.substring(start) : line.substring(start, end);
        if (value.indexOf('"') >= 0) {
            return -1;
        }
        field.append(value);
        return end < 0 ? line.length() : end;
    }

    /** 整行错误。 */
    static BinImportRowError rowError(Line line, BinImportErrorCode code, String message) {
        return new BinImportRowError(line.lineNo(), null, code, message, null);
    }

    /** 列错误，附带安全的值摘录。 */
    static BinImportRowError columnError(Line line, String column, BinImportErrorCode code, String message, String value) {
        return new BinImportRowError(line.lineNo(), column, code, message, excerpt(value));
    }

    /**
     * 安全的值摘录
     *
     * @param value 原值
     * @return 含超过 8 位连续数字或为空时为 null，否则截断到 64 字符
     */
    public static String excerpt(String value) {
        if (value == null || value.isEmpty() || LONG_DIGITS.matcher(value).find()) {
            return null;
        }
        return value.length() > MAX_EXCERPT_LENGTH ? value.substring(0, MAX_EXCERPT_LENGTH) : value;
    }

    /**
     * 一个物理行
     *
     * @param lineNo 行号，从 1 起
     * @param text 严格解码后的文本，不含换行；编码非法、含 NUL 或超长时为 null
     */
    record Line(int lineNo, String text) {
        /** 本行编码非法、含 NUL 或超过行字节上限。 */
        boolean encodingInvalid() {
            return text == null;
        }
    }

    /** 第一遍的累计状态：数据行数、错误行、前 200 条错误与重复前缀。 */
    static final class ValidationState {
        private final Map<String, String> regions;
        private final Map<Integer, Integer> firstLineByPrefix = new HashMap<>();
        private final BitSet duplicateFirstLines = new BitSet();
        private final BitSet errorLines = new BitSet();
        private final List<BinImportRowError> errors = new ArrayList<>();
        private int dataRows;
        private int pendingEmptyLines;
        private int firstPendingEmptyLine;

        ValidationState(Map<String, String> regions) {
            this.regions = regions;
        }

        /** 处理一行：中间空行在遇到下一条非空行时记错，末尾空行忽略。 */
        void accept(Line line) {
            if (line.text() != null && line.text().isEmpty()) {
                if (pendingEmptyLines++ == 0) {
                    firstPendingEmptyLine = line.lineNo();
                }
                return;
            }
            for (int index = 0; index < pendingEmptyLines; index++) {
                Line empty = new Line(firstPendingEmptyLine + index, "");
                dataRows++;
                record(List.of(rowError(empty, BinImportErrorCode.COLUMN_COUNT_MISMATCH, "中间不允许空行")));
            }
            pendingEmptyLines = 0;
            dataRows++;
            List<BinImportRowError> rowErrors = new ArrayList<>();
            BinRangeRow row = parseRow(line, regions, rowErrors);
            if (row != null) {
                checkDuplicate(line, row.getBinPrefix(), rowErrors);
            }
            record(rowErrors);
        }

        /** 同一前缀再次出现时，本行与首次出现的行都记 DUPLICATE_PREFIX。 */
        void checkDuplicate(Line line, String binPrefix, List<BinImportRowError> rowErrors) {
            int key = Integer.parseInt(binPrefix) * 3 + binPrefix.length() - 6;
            Integer firstLine = firstLineByPrefix.putIfAbsent(key, line.lineNo());
            if (firstLine == null) {
                return;
            }
            rowErrors.add(duplicate(line.lineNo(), binPrefix));
            if (!duplicateFirstLines.get(firstLine)) {
                duplicateFirstLines.set(firstLine);
                record(List.of(duplicate(firstLine, binPrefix)));
            }
        }

        /** 重复前缀错误。 */
        static BinImportRowError duplicate(int lineNo, String binPrefix) {
            return new BinImportRowError(lineNo, COLUMN_BIN_PREFIX, BinImportErrorCode.DUPLICATE_PREFIX, "同一前缀在文件中出现多次",
                excerpt(binPrefix));
        }

        /** 记录错误行，并保留前 200 条明细。 */
        void record(List<BinImportRowError> rowErrors) {
            for (BinImportRowError error : rowErrors) {
                errorLines.set(error.getLineNo());
                if (errors.size() < MAX_RECORDED_ERRORS) {
                    errors.add(error);
                }
            }
        }
    }

    /** 按字节读取物理行并同时计算 SHA-256 与已读字节数；只解码单行，不缓存整份文件。 */
    static final class LineSource {
        private final InputStream in;
        private final MessageDigest digest;
        private final byte[] buffer = new byte[8192];
        private int bufferLength;
        private int bufferPosition;
        private long bytesRead;
        private int lineNo;
        private boolean finished;

        LineSource(InputStream in) {
            this.in = in;
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException unavailable) {
                throw new ErrorException("SHA-256 不可用", unavailable);
            }
        }

        /** 下一物理行；文件结束返回 null（以换行结尾时不产生额外空行）。 */
        Line next() throws IOException {
            if (finished) {
                return null;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            boolean tooLong = false;
            boolean newline = false;
            for (int current = read(); current >= 0; current = read()) {
                if (current == '\n') {
                    newline = true;
                    break;
                }
                if (bytes.size() < MAX_LINE_BYTES) {
                    bytes.write(current);
                } else {
                    tooLong = true;
                }
            }
            if (!newline) {
                finished = true;
                if (bytes.size() == 0 && !tooLong) {
                    return null;
                }
            }
            lineNo++;
            return new Line(lineNo, tooLong ? null : decode(bytes.toByteArray()));
        }

        /** 严格解码一行：去掉行尾 CR 与第 1 行的 BOM；非法 UTF-8 或含 NUL 时返回 null。 */
        String decode(byte[] raw) {
            int start = lineNo == 1 && raw.length >= 3 && Arrays.equals(raw, 0, 3, BOM, 0, 3) ? 3 : 0;
            int end = raw.length > start && raw[raw.length - 1] == '\r' ? raw.length - 1 : raw.length;
            for (int index = start; index < end; index++) {
                if (raw[index] == 0) {
                    return null;
                }
            }
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw, start, end - start))
                    .toString();
            } catch (CharacterCodingException invalid) {
                return null;
            }
        }

        /** 读取一个字节并计入摘要。 */
        int read() throws IOException {
            if (bufferPosition == bufferLength) {
                bufferLength = in.read(buffer);
                bufferPosition = 0;
                if (bufferLength <= 0) {
                    bufferLength = 0;
                    return -1;
                }
                digest.update(buffer, 0, bufferLength);
                bytesRead += bufferLength;
            }
            return buffer[bufferPosition++] & 0xFF;
        }

        /** 已读字节数。 */
        long bytesRead() {
            return bytesRead;
        }

        /** 已读全部字节的 SHA-256 小写十六进制；须在读到文件末尾后调用。 */
        String contentHash() {
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
