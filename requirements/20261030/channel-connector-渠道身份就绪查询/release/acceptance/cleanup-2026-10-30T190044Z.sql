-- REQ-02 验收合成运营账号停用草稿（不自动执行；主会话审阅后经运营控制台停用或按 runbook 执行）
-- 账号 r02mt-nomid-1030224939@example.test（accountId 241996993830928384）、角色 241996993143062528
SELECT account_id, status FROM mor_platform.t_user_account WHERE account_id = 241996993830928384;
