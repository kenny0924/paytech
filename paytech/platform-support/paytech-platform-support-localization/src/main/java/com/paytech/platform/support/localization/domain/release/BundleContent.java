package com.paytech.platform.support.localization.domain.release;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 语言包内容的规范字节：扁平 {"key":"text"}，key 按字典序，UTF-8，无多余空白；同一内容总得到相同字节。 */
public final class BundleContent {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private BundleContent() {
    }

    /**
     * 写出规范字节。
     *
     * @param texts key 到文案
     * @return UTF-8 字节
     */
    public static byte[] write(Map<String, String> texts) {
        return JSON.writeValueAsBytes(new TreeMap<>(texts));
    }

    /**
     * 读取语言包；内容不是扁平字符串对象时报错。
     *
     * @param content UTF-8 字节
     * @return key 到文案，按字典序
     */
    public static SortedMap<String, String> read(byte[] content) {
        try {
            return new TreeMap<>(JSON.readValue(content, new TypeReference<Map<String, String>>() {
            }));
        } catch (JacksonException corrupted) {
            throw new IllegalStateException("语言包内容不是扁平字符串对象", corrupted);
        }
    }
}
