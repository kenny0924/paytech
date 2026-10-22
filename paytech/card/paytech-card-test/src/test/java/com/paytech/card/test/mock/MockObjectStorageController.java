package com.paytech.card.test.mock;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.card.test.mocker.PlatformMocker;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的对象存储短时下载地址：按文件 ID 返回配置的 CSV 字节。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockObjectStorageController {
    /** 下载地址前缀。 */
    static final String PATH_PREFIX = "/mock-object-storage/files/";

    private final PlatformMocker platform;

    /** 以附件字节返回文件原版本。 */
    @GetMapping(PATH_PREFIX + "{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable("fileId") String fileId) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv")).body(platform.download(fileId));
    }
}
