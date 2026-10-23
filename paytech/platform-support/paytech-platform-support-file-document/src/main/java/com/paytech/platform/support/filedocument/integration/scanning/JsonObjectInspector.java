package com.paytech.platform.support.filedocument.integration.scanning;

import java.io.InputStream;

import com.paytech.foundation.common.error.ServiceUnavailableException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.json.JsonFactory;

/**
 * 流式检查内容是否恰好是一个 JSON 对象：根为对象，对象结束后只有空白；不构建对象树，内存有界。
 * <li>只做语法检查，不检查重复键或业务结构；业务含义由读取方（如多语言导入）自行校验。</li>
 * <li>编码由调用方保证为 UTF-8（{@code StoredContentType} 已排除 BOM 与 NUL）。</li>
 */
final class JsonObjectInspector {

    /** 不自动关闭来源：摘要流在解析后还要读完剩余字节。 */
    private static final JsonFactory FACTORY = JsonFactory.builder().disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
        .build();

    private JsonObjectInspector() {
    }

    /**
     * 解析整个输入。
     *
     * @param bytes 文件字节；解析器只读取，不关闭
     * @return 恰好一个 JSON 对象为真；语法错误、根不是对象或对象后还有内容为假
     * @throws ServiceUnavailableException 读取存储失败；由补偿重试，不把读取失败当作格式不合法
     */
    static boolean isObject(InputStream bytes) {
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), bytes)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return false;
            }
            parser.skipChildren();
            return parser.nextToken() == null;
        } catch (JacksonIOException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        } catch (JacksonException malformed) {
            return false;
        }
    }
}
