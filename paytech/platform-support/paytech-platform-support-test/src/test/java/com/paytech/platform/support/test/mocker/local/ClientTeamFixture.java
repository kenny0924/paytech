package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.test.dal.ClientTeamTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 团队与邀请测试的邀请邮件模板：内容与初始化 DML 一致，只路由到 SES 测试账号
 * <li>邀请、成员、角色与挑战行属于 PlatformDataCleaner 整表回收的表；本组件只按 ID 清理自建模板三行。</li>
 */
@TestComponent
@Order(-200)
@RequiredArgsConstructor
public class ClientTeamFixture implements Clear {

    /** 与初始化 DML 一致的邀请邮件主题。 */
    public static final String INVITATION_SUBJECT = "PAYTECH · 邀请您加入工作区";

    /** 与初始化 DML 一致的邀请邮件正文。 */
    public static final String INVITATION_BODY = "邀请您加入工作区\n\n您被邀请加入一个 PAYTECH Client 工作区。请打开以下链接，"
        + "使用收到本邮件的邮箱登录或注册后接受邀请：\n${link}\n\n邀请 7 天内有效。如果您不认识发起人，请忽略此邮件。";

    private final ClientTeamTestMapper mapper;
    private boolean template;

    /** 插入邀请邮件模板、内容与路由（需先由 NotificationFixture 插入 SES 测试账号）。 */
    public void prepareInvitationTemplate() {
        assertAll(() -> assertEquals(1, mapper.insertInvitationTemplate()),
            () -> assertEquals(1, mapper.insertInvitationContent(INVITATION_SUBJECT, INVITATION_BODY)),
            () -> assertEquals(1, mapper.insertInvitationRoute()));
        template = true;
    }

    /** 按 ID 清理自建模板三行。 */
    @Override
    public void clear() {
        if (!template) {
            return;
        }
        mapper.deleteInvitationRoute();
        mapper.deleteInvitationContent();
        mapper.deleteInvitationTemplate();
        template = false;
    }
}
