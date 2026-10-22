package com.paytech.platform.support.filedocument.integration.storage;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 逐块检查字节是否为不含 NUL 的严格 UTF-8 文本；内存有界，跨块的多字节字符由下一块补齐。
 * <li>严格解码：拒绝过长编码、代理区码点与非法续字节；开头 BOM 是合法 UTF-8，由业务读取方自行去除。</li>
 * <li>一个实例只检查一份内容，非线程安全。</li>
 */
public final class Utf8TextInspector {
    /** UTF-8 多字节字符最长 4 字节，跨块最多遗留 3 字节。 */
    private static final int MAX_PENDING = 3;
    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
    private final CharBuffer sink = CharBuffer.allocate(8192);
    private byte[] pending = new byte[0];
    private boolean valid = true;

    /**
     * 一次性判断一段字节是否为文本。
     *
     * @param bytes 字节
     * @param truncated 内容在此处被截断时为真，末尾不完整的多字节字符不算非法
     * @return 不含 NUL 且是合法 UTF-8
     */
    public static boolean isText(byte[] bytes, boolean truncated) {
        Utf8TextInspector inspector = new Utf8TextInspector();
        inspector.update(bytes, 0, bytes.length);
        return truncated ? inspector.validSoFar() : inspector.finish();
    }

    /**
     * 追加一块字节；发现 NUL 或非法编码后后续输入不再检查。
     *
     * @param bytes 缓冲区
     * @param offset 起始位置
     * @param length 字节数
     */
    public void update(byte[] bytes, int offset, int length) {
        if (!valid) {
            return;
        }
        for (int i = offset; i < offset + length; i++) {
            if (bytes[i] == 0) {
                valid = false;
                return;
            }
        }
        ByteBuffer input = ByteBuffer.allocate(pending.length + length);
        input.put(pending).put(bytes, offset, length).flip();
        valid = decode(input, false);
        pending = new byte[input.remaining()];
        input.get(pending);
        valid = valid && pending.length <= MAX_PENDING;
    }

    /** 到目前为止没有发现 NUL 或非法编码；末尾未补齐的多字节字符视为待续。 */
    public boolean validSoFar() {
        return valid;
    }

    /** 内容结束：此前合法且没有残留的不完整字符。 */
    public boolean finish() {
        return valid && decode(ByteBuffer.wrap(pending), true) && decoder.flush(sink.clear()).isUnderflow();
    }

    /** 解码并丢弃字符，只关心是否出错；未结束时不完整的尾部留在 input 中。 */
    private boolean decode(ByteBuffer input, boolean endOfInput) {
        while (true) {
            CoderResult result = decoder.decode(input, sink.clear(), endOfInput);
            if (result.isError()) {
                return false;
            }
            if (result.isUnderflow()) {
                return !endOfInput || !input.hasRemaining();
            }
        }
    }
}
