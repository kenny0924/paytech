package com.paytech.tax.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.tax.test.mocker.external.PlatformFileMocker;

/** 仅存在于独立测试 HTTP 服务的对象存储直传地址：按上传 ID 保存 PUT 的字节。 */
@RestController
@TestComponent
public class MockObjectStorageController {
    /** 直传地址前缀。 */
    static final String PATH_PREFIX = "/mock-object-storage/uploads/";

    private final PlatformFileMocker platform;

    /** 注入 Mocker。 */
    public MockObjectStorageController(PlatformFileMocker platform) {
        this.platform = platform;
    }

    /** 保存字节，返回 200。 */
    @PutMapping(PATH_PREFIX + "{uploadId}")
    public ResponseEntity<Void> put(@PathVariable("uploadId") String uploadId, @RequestBody byte[] content) {
        platform.put(uploadId, content);
        return ResponseEntity.ok().build();
    }
}
