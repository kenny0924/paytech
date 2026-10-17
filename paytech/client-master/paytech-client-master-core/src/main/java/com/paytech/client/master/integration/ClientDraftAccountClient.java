package com.paytech.client.master.integration;
/** Platform 是当前 CUSTOMER 账号和 CLIENT 会话的唯一权威。 */
public interface ClientDraftAccountClient { void authorize(long accountId,long sessionId); }
