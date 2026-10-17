package com.paytech.data.domainservice.impl;

import com.paytech.data.core.configuration.CdcWhitelistProperties;
import com.paytech.data.domain.whitelist.WhitelistedTable;
import com.paytech.data.domainservice.WhitelistCatalog;
import com.paytech.foundation.common.error.ErrorException;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * 启动时读取并校验 CDC 白名单契约文件；校验规则见 {@link WhitelistDocumentParser}。
 *
 * @author Claude
 */
@Service
public class WhitelistCatalogImpl implements WhitelistCatalog {

    private final List<WhitelistedTable> tables;

    /**
     * 读取配置位置的白名单文件，非法内容直接失败。
     *
     * @param properties 白名单配置
     * @param resourceLoader 资源加载器
     */
    public WhitelistCatalogImpl(CdcWhitelistProperties properties, ResourceLoader resourceLoader) {
        String location = properties.getWhitelistLocation();
        if (location == null || location.isBlank()) {
            throw new ErrorException("CDC 白名单位置未配置: data-service.cdc.whitelist-location");
        }
        this.tables = load(resourceLoader.getResource(location), location);
    }

    @Override
    public List<WhitelistedTable> tables() {
        return tables;
    }

    @Override
    public Optional<WhitelistedTable> find(String database, String table) {
        return tables.stream()
            .filter(candidate -> candidate.database().equals(database) && candidate.name().equals(table))
            .findFirst();
    }

    private static List<WhitelistedTable> load(Resource resource, String location) {
        if (!resource.exists()) {
            throw new ErrorException("CDC 白名单文件不存在: " + location);
        }
        try (InputStream input = resource.getInputStream()) {
            return new WhitelistDocumentParser(location).parse(input);
        } catch (IOException exception) {
            throw new ErrorException("CDC 白名单文件读取失败: " + location, exception);
        }
    }
}
