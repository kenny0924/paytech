package com.paytech.commerce.integration.customer;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.commerce.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.commerce.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.FileLink;
import com.paytech.commerce.domainservice.port.customer.CheckoutDocumentPort;

import lombok.AllArgsConstructor;

/** 将原冻结文件坐标转换为受控链接；仅在数据库事务外由用例入口调用。 */
@Component
@AllArgsConstructor
public class CheckoutDocumentLinkClient {
    private final CheckoutDocumentPort documents;

    /** 只查询原三份合同的文件链接，准备响应不会顺带调用 Payment。 */
    public List<FileLink> links(FrozenCheckoutRecord frozen, Instant expiresAt, Instant now) {
        if (frozen == null) {
            return List.of();
        }
        return List.of(link(frozen.renderedFileId(), frozen.renderedContentHash(), expiresAt, now),
            link(frozen.refundPolicyFileId(), frozen.refundPolicyContentHash(), expiresAt, now),
            link(frozen.privacyNoticeFileId(), frozen.privacyNoticeContentHash(), expiresAt, now));
    }

    /** 只接受来源 Owner 返回的 HTTPS 原文件链接，短期授权不超过 Checkout。 */
    public FileLink link(long id, String hash, Instant expiresAt, Instant now) {
        var link = documents.link(id, hash, expiresAt);
        if (link == null || link.url() == null || link.url().length() > 4096
            || (link.expiresAt() != null && (!link.expiresAt().isAfter(now) || link.expiresAt().isAfter(expiresAt)))) {
            throw new CheckoutUpstreamProtocolException("冻结文件链接不可用");
        }
        try {
            var uri = URI.create(link.url());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("非受控 HTTPS 链接");
            }
        } catch (IllegalArgumentException invalid) {
            throw new CheckoutUpstreamProtocolException("冻结文件链接协议无效", invalid);
        }
        return link;
    }

}
