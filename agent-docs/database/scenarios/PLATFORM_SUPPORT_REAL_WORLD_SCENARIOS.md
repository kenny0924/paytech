# Platform Support 现实场景与逐表字段映射

最后更新：2026-08-29

状态：与 92 表 Platform Support V1 PDManer 基线同步；自动场景覆盖已建立，PDManer 4.9.3 应用内人工检查待完成

> **历史场景警告：** 本文的外部 IdP 登录和动态 Gateway Route/Release 场景仍用于解释冻结 92 表 V1 数据，但其未来实现语义已被 [Platform Support 内部运营认证需求](../../plans/2026-08-29-platform-support-operations-authentication-requirements.md) 取代。内部 Operations 改为 Platform 自有账号/密码校验值与 Refresh Session；首期 Gateway 使用静态 Route/Upstream 和跨维度累计容量 Guard。完成新的物理模型设计前，不得把相关示例实现为 Platform 代码或 Migration。

## 1. 固定参与方与编号

本文用同一组稳定示例贯穿身份接入、文件与消费者文档、通知、Webhook、审计、Gateway、技术配置和可靠执行：

| 对象 | 示例值 |
|---|---|
| Client / Access Tenant | `200001` / `210001` |
| Platform User / Tenant Member | `220001` / `220011` |
| Order / Order Context / Refund | `410001` / `410021` / `410031` |
| Receipt / Credit Note | `420001` / `420002` |
| Notification Request / Delivery | `510001` / `510021` |
| Webhook Event / Original Delivery / Replay Delivery | `610001` / `610011` / `610012` |
| Gateway LKG / Candidate / Rollback Release | `710001` / `710002` / `710003` |
| Config Change Set / Release | `810001` / `810011` |

Client、Order、Refund、Fiscal Record 等编号只是在 Platform Support 中冻结的来源系统引用，不建立外部关系。文件正文进入加密对象存储；数据库只保存 File ID 与 Content Hash。非卡敏感值进入中央 Vault，Platform 只按当前 Reveal、等值查询、抑制或展示消费者保存最小 Vault Token、Blind Index/Key Version 或 Masked Value。

## 2. SCN-PLT-01：外部 IdP、Tenant 授权、API Credential、Readiness 与业务 Email OTP

Client `200001` 使用外部 IdP 完成登录和 MFA；Platform 验证 Token 后把 Subject 映射为 User `220001`，再通过 Tenant Member、Role 和 Permission 形成授权上下文。Client 创建一把 API Credential 并完成 Webhook/配置 Readiness 检查。Consumer Email Ownership Challenge 由 Notification 发送，第二次输入匹配后终止活动 Slot。Platform 不保存密码、IdP Token、MFA Secret 或登录 Session；业务 Email OTP 绝不用于登录 MFA、管理员提权或财务双签。

### 2.1 身份、授权与接入 16 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_idp_connection` | `connection_id=10001，connection_code=WORKFORCE_OIDC，issuer=issuer.example，audience=code-mor，status=ACTIVE，config_version=3，version=2`；只保存公开验签配置。 |
| `t_access_tenant` | `tenant_id=210001，tenant_type=CLIENT，owner_ref_id=200001，tenant_name=Nova，status=ACTIVE，version=1`；不是 Client 主档。 |
| `t_user_identity` | `user_id=220001，idp_connection_id=10001，idp_subject=sub-220001，email_vault_token=vt-email-220001，email_blind_index=h-email，email_blind_key_ver=4，email_masked=k***@example.test，status=ACTIVE，version=1`。 |
| `t_tenant_member` | `member_id=220011，tenant_id=210001，user_id=220001，member_type=OWNER，status=ACTIVE，invited_by=220001，version=2`。 |
| `t_role` | `role_id=230001，tenant_scope=TENANT，tenant_id=210001，scope_key=210001，role_code=CLIENT_ADMIN，status=ACTIVE，system_role=0，version=1`。 |
| `t_permission` | `permission_id=230011，permission_code=WEBHOOK_MANAGE，resource_type=WEBHOOK_ENDPOINT，action_code=UPDATE，risk_level=HIGH，status=ACTIVE，version=1`。 |
| `t_member_role` | `member_role_id=230021，tenant_member_id=220011，tenant_id=210001，role_id=230001，role_scope_key=210001，effective_from=2026-08-27T08:00:00Z，effective_to=NULL，status=ACTIVE，version=1`；两个复合 FK 禁止跨 Tenant 角色授权，Platform Scope 角色则冻结 `role_scope_key=PLATFORM`。 |
| `t_role_permission` | `role_permission_id=230031，role_id=230001，permission_id=230011，status=ACTIVE，version=1`；单项撤权把状态改为 REVOKED 并写 Audit，不删除映射或停用整个 Role。 |
| `t_api_credential` | `credential_id=240001，tenant_id=210001，client_id=200001，key_prefix=cm_live_7fa，key_hash=argon2id-hash，status=ACTIVE，rotated_from_id=NULL，version=1`；明文只返回一次。 |
| `t_api_credential_scope` | `scope_id=240011，credential_id=240001，scope_code=ORDER_CREATE，resource_pattern=orders，action_code=CREATE，effective_from=2026-08-27T08:10:00Z，status=ACTIVE，version=1`。 |
| `t_credential_event` | `event_id=240021，credential_id=240001，event_no=1，event_type=CREATED，actor_id=220001，request_id=req-credential-1，occurred_time=2026-08-27T08:10:00Z`。 |
| `t_client_integration_readiness` | `readiness_id=250001，client_id=200001，current_evaluation_id=250011，readiness_status=READY，reason_set_hash=h-ready，evaluated_time=2026-08-27T08:20:00Z，version=4`。 |
| `t_integration_readiness_eval` | `evaluation_id=250011，readiness_id=250001，evaluation_no=4，requirement_config_ver_id=810101，requirement_config_hash=h-requirement，input_hash=h-readiness-input，readiness_status=READY，trigger_type=WEBHOOK，trigger_id=endpoint-610101，evaluated_time=2026-08-27T08:20:00Z`。 |
| `t_integration_readiness_check` | `check_id=250021，evaluation_id=250011，check_no=1，capability_code=API_CREDENTIAL，required_flag=1，resource_type=CREDENTIAL，resource_id=240001，resource_version=1，check_result=PASSED，evidence_hash=h-check`。 |
| `t_email_otp_challenge` | `challenge_id=260001，challenge_no=OTP-260001，tenant_scope_key=210001，purpose=EMAIL_OWNERSHIP，active_scope_hash=h-otp-scope，email_vault_token=vt-otp-email，email_blind_index=h-email，email_blind_key_ver=4，email_masked=k***@example.test，max_attempts=5，used_attempts=2，status=VERIFIED，active_slot=NULL，notification_request_id=510001，version=4`；短码只保存 `code_hmac` 与 `pepper_key_ver`。 |
| `t_email_otp_attempt` | `attempt_id=260011，challenge_id=260001，attempt_no=2，result=MATCHED，request_id=req-otp-2，ip_hash=h-ip，attempt_time=2026-08-27T08:31:00Z`；不保存输入短码。 |

同一个 Challenge Key 重试返回原 Challenge；同一 Scope 新建 Challenge 时原活动行先 CAS 终止。API Credential、Scope、Webhook Subscription 或 Requirement Config 变化都会创建新的 Readiness Evaluation，不由定时任务修补旧结论。

## 3. SCN-PLT-02：文件显式重扫、双输入 Consumer Document 与 Credit Note 更正

Commerce 为 Refund `410031` 发布 `ConsumerDocumentRequested`，冻结 Order Context 和三份 Legal File/Hash；Finance 随后发布同一 Generation Key 的 `FiscalRecordCreated`。Platform 原子生成 Credit Note `420002`，通过 `corrects_document_id` 指向 Receipt `420001`。同一文档重新渲染时，新 Version 通过 `supersedes_version_id` 指向旧 Version，原文件永不覆盖。

上传文件第一次通过 Anti-malware/DLP/Format 后进入 AVAILABLE；安全团队按新威胁情报显式重扫，命中后执行 `AVAILABLE → QUARANTINED`，保留原扫描结果、首次可用时间和所有状态历史。

### 3.1 文件与消费者文档 19 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_file_upload` | `upload_id=310001，tenant_id=210001，uploader_id=220001，expected_size=20480，expected_mime=application-pdf，status=COMPLETED，version=3`。 |
| `t_file_scan_policy` | `policy_id=310011，policy_code=DOCUMENT_PDF，current_ver_id=310021，status=ACTIVE，version=2`。 |
| `t_file_scan_policy_ver` | `policy_ver_id=310021，policy_id=310011，policy_ver=2，rule_schema_version=1，max_size_bytes=10485760，content_hash=h-scan-policy，status=ACTIVE，active_slot=1`。 |
| `t_file_scan_requirement` | `requirement_id=310031，policy_ver_id=310021，requirement_no=1，scanner_code=CLAMAV，scan_type=ANTI_MALWARE，minimum_scanner_version=1.4，requirement_hash=h-scan-requirement`。 |
| `t_file_object` | `file_id=320001，creation_source=UPLOAD，upload_id=310001，storage_provider=S3，storage_container_ref=docs，storage_object_ref=obj-a8f7，object_version=v1，content_hash=h-file-320001，size_bytes=20480，mime_type=application-pdf，classification=CONFIDENTIAL，scan_policy_ver_id=310021，scan_status=QUARANTINED，object_status=ACTIVE，available_time=2026-08-27T09:02:00Z，version=5`。 |
| `t_file_owner_ref` | `owner_ref_row_id=320011，file_id=320001，owner_system=COMMERCE，owner_type=ORDER_LEGAL，owner_ref_id=410001，purpose_code=REFUND_POLICY`。 |
| `t_file_scan_result` | `scan_result_id=320021，file_id=320001，scan_requirement_id=310031，scan_policy_ver_id=310011，attempt_no=2，scanner_runtime_version=1.4.2，result_code=MALWARE_FOUND，result_hash=h-scan-result，scanned_time=2026-08-27T12:00:00Z`；两个复合 FK 保证 Requirement 属于文件冻结的同一 Policy Version。 |
| `t_file_state_change` | `state_change_id=320031，file_id=320001，from_status=AVAILABLE，to_status=QUARANTINED，reason_code=RESCAN_THREAT，source_type=SCAN_RESULT，source_ref_id=320021，aggregate_version=5，occurred_time=2026-08-27T12:00:01Z`。 |
| `t_document_template` | `template_id=330001，template_code=UAE_CREDIT_NOTE，document_type=CREDIT_NOTE，current_ver_id=330011，status=ACTIVE，version=2`。 |
| `t_document_template_ver` | `template_ver_id=330011，template_id=330001，template_ver=2，renderer_contract_ver=3，schema_version=1，variable_schema_hash=h-doc-schema，content_hash=h-doc-template，status=ACTIVE`。 |
| `t_document_template_translation` | `translation_id=330021，template_ver_id=330011，locale=en-AE，title_template=credit-note-title，layout_template=credit-note-layout，translation_hash=h-doc-en，status=ACTIVE`。 |
| `t_document_generation` | `generation_id=340001，generation_key_version=1，document_generation_key=h-generation，generation_scope_hash=h-generation-scope，client_id=200001，order_id=410001，business_ref_type=REFUND，business_ref_id=410031，document_type=CREDIT_NOTE，commerce_request_event_key=com-doc-410031，commerce_source_version=3，fiscal_event_key=fin-fiscal-440001，fiscal_record_id=440001，fiscal_source_version=1，status=MATERIALIZED，version=3`；两个来源 Hash 和三份 Legal File/Hash 同时冻结。 |
| `t_doc_generation_state_change` | `state_change_id=340011，generation_id=340001，from_status=WAITING_FISCAL，to_status=MATERIALIZED，source_system=FINANCE，source_event_key=fin-fiscal-440001，source_version=1，source_payload_hash=h-fiscal，source_type=SOURCE_EVENT，source_ref_id=fin-fiscal-440001，aggregate_version=3，occurred_time=2026-08-27T09:10:00Z`。 |
| `t_consumer_document` | `document_id=420002，generation_id=340001，client_id=200001，order_id=410001，document_type=CREDIT_NOTE，consumer_ref_hash=h-consumer，corrects_document_id=420001，current_ver_id=420022，status=GENERATED，version=4`。 |
| `t_document_version` | `document_ver_id=420022，document_id=420002，document_ver=2，supersedes_version_id=420021，document_template_ver_id=330011，document_template_translation_id=330021，renderer_version=3.1，locale=en-AE，file_id=320101，source_set_hash=h-source-set，content_hash=h-credit-note，status=GENERATED`。 |
| `t_document_render_job` | `render_job_id=420031，document_ver_id=420022，job_no=2，status=SUCCEEDED，active_slot=NULL，attempt_count=1，lease_owner=NULL，lease_until=NULL，fencing_token=7，version=3`。 |
| `t_document_delivery` | `delivery_id=420041，document_ver_id=420022，delivery_no=1，recipient_hash=h-consumer，channel=EMAIL，notification_delivery_id=510021，status=DELIVERED，version=4`。 |
| `t_document_access_grant` | `grant_id=420051，grant_no=GRANT-420051，document_ver_id=420022，consumer_ref_hash=h-consumer，order_id=410001，access_action=DOWNLOAD，effective_from=2026-08-27T09:15:00Z，effective_to=2026-08-27T09:30:00Z，status=ACTIVE，active_slot=1，version=1`。 |
| `t_document_state_change` | `state_change_id=420061，document_id=420002，document_ver_id=420022，from_status=CREATED，to_status=GENERATED，source_type=RENDER_JOB，source_ref_id=420031，aggregate_version=4，occurred_time=2026-08-27T09:14:00Z`。 |

若两个来源的算法版本、Generation Key、Scope、Client/Order/Refund、Document Type 或 Payload Hash 不一致，Generation 进入 CONFLICT，不创建或覆盖文档。Credit Note 更正关系与 Version 替代关系必须同 Owner 且不能自引用；File 尚未 AVAILABLE 时 Version 不能进入 GENERATED。

## 4. SCN-PLT-03：Notification Provider 接受、已验签送达与晚到 COMPLAINT

文档通知 Request `510001` 选择租户级 EMAIL Route，Worker 通过 Lease/Fencing Claim Delivery `510021`。Provider 同步接受只把状态置为 SENT；已验签 DELIVERED Callback 才进入 DELIVERED。两小时后出现晚到 COMPLAINT，Platform 幂等追加 Callback、Suppression 和 State Change，把 Delivery 推进为 COMPLAINED，之后普通通知被拦截。

### 4.1 Notification 与 Email 14 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_notification_provider` | `provider_id=500001，provider_code=EMAIL_A，channel=EMAIL，adapter_code=SES_V2，current_ver_id=500011，status=ACTIVE，version=2`。 |
| `t_notification_provider_ver` | `provider_ver_id=500011，provider_id=500001，provider_ver=2，endpoint_ref=email-endpoint，secret_ref=secret-email-a，callback_verification_key_ref=verify-email-a，callback_algorithm=HMAC_SHA256，capability_schema_version=1，content_hash=h-provider，status=ACTIVE`。 |
| `t_notification_request` | `request_id=510001，source_system=PLATFORM，source_event_key=doc-generated-420022，source_version=4，source_payload_hash=h-doc-event，notification_type=CONSUMER_DOCUMENT，tenant_scope_key=210001，notification_template_ver_id=520011，recipient_set_hash=h-recipient-set，priority=50，status=COMPLETED，version=6`。 |
| `t_notification_recipient` | `recipient_id=510011，request_id=510001，recipient_no=1，channel=EMAIL，address_vault_token=vt-recipient，address_blind_index=h-email，address_blind_key_ver=4，address_masked=k***@example.test，locale=en-AE`。 |
| `t_notification_message` | `message_id=510012，request_id=510001，recipient_id=510011，notify_template_translation_id=520021，message_vault_token=vt-message，subject_masked=Credit-note，body_preview_masked=Your-document，content_hash=h-message，sensitivity=CONFIDENTIAL，expires_time=2026-08-28T10:00:00Z`。 |
| `t_notification_delivery` | `delivery_id=510021，request_id=510001，recipient_id=510011，notification_route_id=500021，route_hash=h-route，status=COMPLAINED，next_retry_time=NULL，attempt_count=1，lease_owner=NULL，lease_until=NULL，fencing_token=11，version=7`。 |
| `t_notification_attempt` | `attempt_id=510031，delivery_id=510021，attempt_no=1，provider_id=500001，provider_ver_id=500011，fencing_token=11，provider_request_key=notify-510021-1，provider_message_id=msg-a-777，request_hash=h-send，response_code=ACCEPTED，result=ACCEPTED，started_time=2026-08-27T10:00:00Z，finished_time=2026-08-27T10:00:01Z`。 |
| `t_notification_route` | `route_id=500021，route_code=DOC_EMAIL，route_ver=3，tenant_id=210001，tenant_scope_key=210001，notification_type=CONSUMER_DOCUMENT，channel=EMAIL，notification_template_ver_id=520011，primary_provider_ver_id=500011，priority=10，effective_from=2026-08-01T00:00:00Z，status=ACTIVE，active_slot=1，version=2`。 |
| `t_notification_provider_callback` | `callback_id=510041，provider_id=500001，provider_message_id=msg-a-777，provider_event_id=complaint-991，callback_dedup_key=EVENT-complaint-991，callback_type=COMPLAINT，occurred_time=2026-08-27T12:00:00Z，payload_hash=h-callback，signature_status=VERIFIED，verification_provider_ver_id=500011，verification_time=2026-08-27T12:00:01Z，verification_failure_code=NULL，notification_attempt_id=510031，notification_delivery_id=510021`。 |
| `t_notification_suppression` | `suppression_id=510051，channel=EMAIL，address_blind_index=h-email，address_blind_index_key_version=4，reason_code=COMPLAINT，source_callback_id=510041，effective_from=2026-08-27T12:00:01Z，expires_time=NULL，status=ACTIVE，active_slot=1，version=1`。 |
| `t_notification_template` | `template_id=520001，template_code=CONSUMER_DOCUMENT_EMAIL，channel=EMAIL，purpose=DOCUMENT_DELIVERY，current_ver_id=520011，status=ACTIVE，version=2`。 |
| `t_notification_template_ver` | `template_ver_id=520011，template_id=520001，template_ver=2，schema_version=1，variable_schema_hash=h-notify-schema，content_hash=h-notify-template，status=ACTIVE`。 |
| `t_notify_template_translation` | `translation_id=520021，template_ver_id=520011，locale=en-AE，subject_template=document-ready，body_template=document-body，translation_hash=h-notify-en，status=ACTIVE`。 |
| `t_notify_delivery_state_change` | `state_change_id=510061，delivery_id=510021，from_status=DELIVERED，to_status=COMPLAINED，reason_code=PROVIDER_COMPLAINT，source_type=CALLBACK，source_ref_id=510041，aggregate_version=7，occurred_time=2026-08-27T12:00:02Z`。 |

验签失败或算法不支持的 Callback 仍冻结实际 `verification_provider_ver_id + verification_time + verification_failure_code`，但 Attempt/Delivery 两个可信锚点必须为空，只写 Security/Audit 且不推进业务状态。无法定位 Provider/Message/Attempt/Delivery 的已验签输入同样 Fail Closed。Complaint 不能被“法定通知”开关绕过，只能选择另一个已批准 Channel。

## 5. SCN-PLT-04：Webhook Endpoint 验证、冻结 Event、失败重试与人工 Replay

Client Endpoint `610101` 的 URL 由 Vault 保护，DNS/SSRF/Challenge 全部通过后 Version `610111` ACTIVE。Payment 业务事实被转换为 Client Schema Version `3` 的 Webhook Event `610001`。原始 Delivery `610011` 成功后不自动重发；Operations 依据工单发起 Replay `610031`，创建 Generation `1` 的新 Delivery `610012`，原结果保持不变。

### 5.1 Client Webhook 11 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_webhook_endpoint` | `endpoint_id=610101，tenant_id=210001，client_id=200001，endpoint_code=PAYMENT_EVENTS，current_ver_id=610111，status=ACTIVE，version=4`。 |
| `t_webhook_endpoint_ver` | `endpoint_ver_id=610111，endpoint_id=610101，endpoint_ver=3，url_vault_token=vt-webhook-url，url_masked=https-example-hook，host_name=hooks.example.test，timeout_ms=3000，content_hash=h-endpoint，status=ACTIVE`。 |
| `t_webhook_endpoint_verification` | `verification_id=610121，endpoint_ver_id=610111，verification_no=2，challenge_hash=h-challenge，resolved_ip_hash=h-public-ip，http_status=204，result=PASSED，failure_code=NULL，verified_time=2026-08-27T07:00:00Z`。 |
| `t_webhook_secret_ver` | `secret_ver_id=610131，endpoint_id=610101，secret_ver=4，secret_ref=secret-webhook-4，fingerprint=h-secret，effective_from=2026-08-27T00:00:00Z，effective_to=NULL，status=ACTIVE`。 |
| `t_webhook_subscription` | `subscription_id=610141，subscription_no=SUB-610141，endpoint_id=610101，event_type=PaymentSucceeded，subscription_ver=2，payload_schema_version=3，effective_from=2026-08-27T00:00:00Z，status=ACTIVE，active_slot=1，version=2`。 |
| `t_webhook_sub_state_change` | `state_change_id=610151，subscription_id=610141，from_status=DRAFT，to_status=ACTIVE，reason_code=VERIFIED，source_type=OPERATOR_COMMAND，source_ref_id=cmd-sub-1，aggregate_version=2，occurred_time=2026-08-27T07:05:00Z`。 |
| `t_webhook_event` | `event_id=610001，client_id=200001，source_system=PAYMENT，source_event_key=payment-510011-v5，source_version=5，source_payload_hash=h-payment-source，event_type=PaymentSucceeded，payload_schema_version=3，payload_storage_mode=VAULT_TOKEN，payload_inline=NULL，payload_vault_token=vt-webhook-payload，payload_hash=h-client-payload`；内部 Token 不进入外部 Payload。非敏感冻结载荷改用 `INLINE + payload_inline`，两种模式严格 XOR。 |
| `t_webhook_delivery` | 原始 `delivery_id=610011，webhook_event_id=610001，client_id=200001，endpoint_id=610101，endpoint_ver_id=610111，subscription_id=610141，payload_schema_version=3，generation_no=0，replay_request_id=NULL，status=SUCCEEDED，attempt_count=2，fencing_token=14，version=5`；Replay 新投递 `delivery_id=610012，generation_no=1，replay_request_id=610031，status=SUCCEEDED，attempt_count=1，fencing_token=15，version=3`。 |
| `t_webhook_attempt` | `attempt_id=610021，delivery_id=610012，endpoint_id=610101，attempt_no=1，secret_ver_id=610131，fencing_token=15，request_key=webhook-610012-1，request_hash=h-webhook-request，response_code=204，result=SUCCEEDED，started_time=2026-08-27T13:00:00Z，finished_time=2026-08-27T13:00:01Z`。 |
| `t_webhook_replay` | `replay_id=610031，original_delivery_id=610011，replay_no=1，requested_by=220001，reason_code=CLIENT_REQUEST，new_delivery_id=610012，status=COMPLETED，version=3`。 |
| `t_webhook_delivery_state_change` | `state_change_id=610041，delivery_id=610012，from_status=PROCESSING，to_status=SUCCEEDED，reason_code=HTTP_204，source_type=REPLAY，source_ref_id=610031，aggregate_version=3，occurred_time=2026-08-27T13:00:01Z`。 |

URL 变化必须创建新的 Vault Record、Endpoint Version 和 Verification。私网、Loopback、Link-local、重绑定 DNS 或 Challenge 失败均阻断 ACTIVE。Replay 不修改原 Attempt、State Change 或发布结果。

## 6. SCN-PLT-05：统一 Audit Hash Chain、Vault Reveal 审计与 Security Alert

User `220001` 下载 Credit Note。授权成功、短期 URL 签发及 Vault Reveal 各形成统一 Audit Event；敏感属性只保存 Token/Masked/Hash。UTC 日终生成 Integrity Point 和 WORM Archive File。Webhook 验签失败另形成 Security Event，聚合到 Alert 并由 Operations 处置。

### 6.1 Audit 与 Security 9 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_audit_chain` | `chain_id=620001，chain_key=2026-08-27-shard-03，partition_date=2026-08-27，shard_no=3，last_sequence_no=1042，last_event_hash=h-audit-1042，status=SEALED，version=1043`。 |
| `t_audit_event` | `audit_event_id=620011，chain_id=620001，sequence_no=1042，canonical_version=1，attribute_root_hash=h-attrs，event_hash=h-audit-1042，schema_version=1，actor_type=USER，actor_id=220001，tenant_id=210001，action_code=DOCUMENT_DOWNLOAD，resource_system=PLATFORM，resource_type=CONSUMER_DOCUMENT，resource_id=420002，result=SUCCEEDED，request_id=req-download，trace_id=trace-download，occurred_time=2026-08-27T14:00:00Z，received_time=2026-08-27T14:00:00Z，payload_hash=h-audit-payload`。 |
| `t_audit_event_attr` | `attr_id=620021，audit_event_id=620011，attr_no=1，attr_key=recipient，value_text=NULL，value_vault_token=vt-audit-recipient，value_masked=k***@example.test，value_hash=h-recipient，classification=RESTRICTED`。 |
| `t_audit_integrity_point` | `integrity_point_id=620031，chain_id=620001，sequence_from=1，sequence_to=1042，start_event_hash=h-audit-1，end_event_hash=h-audit-1042，root_hash=h-audit-root，signature_ref=sig-audit-20260827，file_id=620101，content_hash=h-integrity-file`。 |
| `t_audit_archive_batch` | `archive_batch_id=620041，archive_no=ARCH-20260827-03，chain_id=620001，range_from=1，range_to=1042，start_event_hash=h-audit-1，end_event_hash=h-audit-1042，file_id=620102，content_hash=h-archive-file，verification_status=VERIFIED`。 |
| `t_security_event` | `security_event_id=630001，event_type=WEBHOOK_SIGNATURE_FAILED，severity=HIGH，actor_ref=provider-a，tenant_id=210001，source_system=PLATFORM，source_event_key=security-callback-9，source_version=1，source_payload_hash=h-security-source，schema_version=1，event_hash=h-security-event，occurred_time=2026-08-27T14:05:00Z`。 |
| `t_security_alert` | `alert_id=630011，alert_no=SEC-2026-001，alert_type=WEBHOOK_ABUSE，severity=HIGH，status=INVESTIGATING，owner_id=220001，version=3`。 |
| `t_security_alert_event` | `alert_event_id=630021，alert_id=630011，security_event_id=630001`。 |
| `t_security_alert_action` | `action_id=630031，alert_id=630011，action_no=2，action_type=INVESTIGATE，actor_id=220001，reason_code=VERIFY_PROVIDER，occurred_time=2026-08-27T14:06:00Z`。 |

审计链尾指针、Event、Attributes 和业务写入按约定事务顺序提交。Archive 只建立可验证副本，不自动删除热数据。Platform 不保存被 Reveal 的敏感原值。

## 7. SCN-PLT-06：Gateway Guard 发布失败保持 Last-Known-Good，随后完整 ROLLBACK

现行 Release `710001` 是 Production/cluster-a 的 Last-Known-Good。Candidate `710002` 包含同 Tier 同 Rule Type 冲突，VALIDATING 失败且未占用 Active Slot；Gateway 继续使用 `710001`。Operations 随后创建新的 ROLLBACK Release `710003`，指向被替代内容和已验证历史来源，重新完成 Item 冻结、Maker-Checker、Activation 与 Instance Ack。

### 7.1 Gateway 保护配置 13 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_gateway_route` | `route_id=700001，route_code=ORDER_CREATE，owner_system_code=COMMERCE，runtime_route_ref=nacos-commerce-order，current_ver_id=700011，status=ACTIVE，version=2`。 |
| `t_gateway_route_ver` | `route_ver_id=700011，route_id=700001，route_ver=2，path_pattern=api-orders，http_method=POST，request_token_cost=2，content_hash=h-route-v2，status=ACTIVE`。 |
| `t_gateway_policy` | `policy_id=700021，policy_code=ORDER_BASELINE，current_ver_id=700031，status=ACTIVE，version=2`。 |
| `t_gateway_policy_ver` | `policy_ver_id=700031，policy_id=700021，policy_ver=3，status=ACTIVE，content_hash=h-policy-v3`。 |
| `t_gateway_guard_rule` | `guard_rule_id=700041，policy_ver_id=700031，rule_no=1，rule_type=RATE_LIMIT，mode=ENFORCE，refill_per_sec=20.000000，burst_capacity=40，request_token_cost=2，reject_status=429`。 |
| `t_gateway_policy_binding` | `binding_id=700051，binding_code=ORDER_GLOBAL，current_ver_id=700061，status=ACTIVE，version=2`。 |
| `t_gateway_policy_binding_ver` | `binding_ver_id=700061，binding_id=700051，binding_ver=2，policy_ver_id=700031，priority_tier=GLOBAL，scope_key=*，route_id=700001，route_scope_key=700001，effective_from=2026-08-01T00:00:00Z，selector_set_hash=h-selectors，content_hash=h-binding，status=ACTIVE，active_slot=1`。 |
| `t_gateway_selector` | `selector_id=700071，binding_ver_id=700061，selector_no=1，selector_type=ENV，selector_value_hash=h-production，cidr_value=NULL，match_mode=INCLUDE，selector_hash=h-selector`。 |
| `t_gateway_release` | 旧 LKG `release_id=710001，release_no=GW-100，environment_code=PRODUCTION，cluster_scope=cluster-a，release_type=NORMAL，items_frozen_time=2026-08-20T10:00:00Z，status=SUPERSEDED，activation_slot=NULL，active_slot=NULL，maker_id=220001，checker_id=220002，version=8`；新回滚 `release_id=710003，release_no=GW-102，release_type=ROLLBACK，rollback_of_release_id=710002，restore_from_release_id=710001，content_hash=h-release-100，items_frozen_time=2026-08-27T15:00:00Z，status=ACTIVE，activation_slot=NULL，active_slot=1，maker_id=220001，checker_id=220002，activated_time=2026-08-27T15:20:00Z，version=7`。 |
| `t_gateway_release_item` | `release_item_id=710011，release_id=710003，item_no=1，item_type=ROUTE，route_ver_id=700011，policy_ver_id=NULL，binding_ver_id=NULL，item_version=2，item_hash=h-route-v2`。 |
| `t_gateway_release_approval` | `approval_id=710021，release_id=710003，approval_role=CHECKER，actor_id=220002，decision=APPROVED，approved_content_hash=h-release-100，decision_time=2026-08-27T15:10:00Z`。 |
| `t_gateway_release_state_change` | `state_change_id=710031，release_id=710003，from_status=ACTIVATING，to_status=ACTIVE，reason_code=ALL_ACKED，source_type=INSTANCE_ACK，source_ref_id=710041，aggregate_version=7，occurred_time=2026-08-27T15:20:00Z`。 |
| `t_gateway_instance_ack` | `ack_id=710041，instance_id=gateway-a-01，release_id=710003，apply_status=APPLIED，applied_hash=h-release-100，applied_time=2026-08-27T15:19:59Z，error_code=NULL`。 |

Binding 匹配顺序固定为 EMERGENCY、CREDENTIAL、CLIENT、ROUTE、GLOBAL；每个 Rule Type 独立继承。Redis 保存 Token Bucket/并发运行计数，MySQL 只保存可审批、可验证、可恢复的发布事实。校验失败的未冻结 Candidate 返回 DRAFT 修改；进入审批后内容不可再变。新 Release Hash 不一致或实例拒绝时进入 `ACTIVATION_FAILED`，旧 LKG 不变。

## 8. SCN-PLT-07：技术配置的 INLINE、VAULT_TOKEN、SECRET_REF 与全有或全无发布

Platform 发布三个纯技术配置：非敏感超时使用 INLINE；需恢复的非卡敏感回调标签使用 VAULT_TOKEN；Provider Secret 使用 SECRET_REF。Change Set 审批绑定完整 Item Hash，Release 对每个 `expected_current_ver_id` 做 CAS，任何漂移都使整批失败，不允许部分激活。Fee、Tax、Risk、MID、Routing、Settlement、Order 或 Refund Policy 不进入该配置域。

### 8.1 Platform 技术配置 8 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_config_namespace` | `namespace_id=800001，namespace_code=PLATFORM_NOTIFICATION，owner_component=NOTIFICATION，status=ACTIVE`。 |
| `t_config_item` | `config_item_id=800011，namespace_id=800001，config_key=provider-timeout-ms，value_type=INTEGER，sensitivity=INTERNAL，current_ver_id=800021，status=ACTIVE，version=3`。 |
| `t_config_version` | INLINE 行 `config_ver_id=800021，config_item_id=800011，config_ver=3，storage_mode=INLINE，inline_value=3000，value_vault_token=NULL，secret_ref=NULL，value_hash=h-inline，schema_version=1，status=ACTIVE`；VAULT_TOKEN 行 `config_ver_id=800022，config_item_id=800012，config_ver=1，storage_mode=VAULT_TOKEN，inline_value=NULL，value_vault_token=vt-config-label，secret_ref=NULL，value_masked=s***，value_hash=h-vault，schema_version=1，status=ACTIVE`；SECRET_REF 行 `config_ver_id=800023，config_item_id=800013，config_ver=2，storage_mode=SECRET_REF，inline_value=NULL，value_vault_token=NULL，secret_ref=secret-provider-a，value_hash=h-secret-ref，schema_version=1，status=ACTIVE`。 |
| `t_config_change_set` | `change_set_id=810001，change_set_no=CFG-2026-001，status=RELEASED，maker_id=220001，checker_id=220002，content_hash=h-change-set，items_frozen_time=2026-08-27T15:50:00Z，version=5`。 |
| `t_config_change_item` | `change_item_id=810002，change_set_id=810001，item_no=1，config_item_id=800011，operation_type=ACTIVATE_VERSION，expected_current_ver_id=800020，target_ver_id=800021，item_hash=h-change-item`。 |
| `t_config_approval` | `approval_id=810003，change_set_id=810001，approval_role=CHECKER，actor_id=220002，decision=APPROVED，approved_content_hash=h-change-set，decision_time=2026-08-27T16:00:00Z`。 |
| `t_config_release` | `release_id=810011，change_set_id=810001，release_no=CFG-REL-001，effective_time=2026-08-27T16:10:00Z，status=ACTIVE，rollback_of_release_id=NULL，content_hash=h-change-set，version=4`。 |
| `t_config_release_state_change` | `state_change_id=810021，release_id=810011，from_status=ACTIVATING，to_status=ACTIVE，reason_code=CAS_SUCCEEDED，source_type=SCHEDULER，source_ref_id=config-activate-810011，aggregate_version=4，occurred_time=2026-08-27T16:10:00Z`。 |

Change Item 在 DRAFT 内受父 Change Set Version 保护地编辑；进入 PENDING_APPROVAL 时原子冻结 Item 集合与 Content Hash，之后不得增删改。Rollback 创建新的 Change Set/Release，不改历史 Version。Secrets Manager/KMS Ref 不是可 Reveal 的业务值，Vault Token 也不被直接返回外部调用方。

## 9. SCN-PLT-08：业务事务内 Compensation、Claim/Lease 与命令幂等

Credit Note Version 进入 GENERATED 的同一本地事务写 `ConsumerDocumentGenerated` 补偿事件。提交后 Dispatcher 以 `lock_owner + lock_until + lock_version` 原子 Claim；实例失联后，XXL-Job 只唤醒服务扫描过期 Lease，新 Worker 递增 Fencing Version 后重试同一 Event Key。HTTP 命令使用 Caller Scope、Operation 和 Idempotency Key 防重，同键不同 Request Hash 立即冲突。

### 9.1 可靠执行 2 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_compensation_event` | `id=900001，event_key=platform-document-420022-v4，event_type=ConsumerDocumentGenerated，execution_type=MQ_PUBLISH，source_type=CONSUMER_DOCUMENT，source_id=420002，source_version=4，schema_version=1，payload_hash=h-comp-payload，execute_status=PROCESSING，execute_count=2，next_execute_at=2026-08-27T16:30:00Z，lock_owner=platform-02，lock_until=2026-08-27T16:31:00Z，lock_version=3`；Broker Confirm 后删除。 |
| `t_command_idempotency` | `id=900011，caller_scope=h-client-200001，operation_type=CREATE_WEBHOOK_REPLAY，idempotency_key=idem-replay-610011-1，request_hash=h-idem-request，response_ref=replay-610031，response_hash=h-idem-response，status=SUCCEEDED，expire_time=2026-09-27T00:00:00Z`；不机械增加 Version。 |

补偿载荷只包含本地业务 ID、Version 和 Hash，不包含 Email、消息正文、Webhook 外部 Payload、对象存储引用、Vault Token 或 Secret。系统不保存 PAN、CVV/CVC、完整磁道、PIN/PIN Block 或卡有效期。专用 Scan/Render/Delivery/Release 状态承担业务执行事实，XXL-Job 只负责兜底唤醒与分片。

## 10. 失败关闭与不创建清单

- IdP Token 验签失败、Tenant/Role 不匹配、API Credential Hash/Scope 不一致：拒绝请求并写 Audit/Security，不建立业务事实。
- OTP Purpose/Context 不匹配、已过期或达到尝试上限：拒绝验证；短码和地址原值不写普通日志。
- File 任一 Requirement 未明确 PASS、内容 Hash 不匹配或重扫命中：不进入 AVAILABLE，必要时隔离且不覆盖历史结果。
- Document 双来源 Version/Key/Scope/身份/Hash 不一致：Generation 进入 CONFLICT，不创建 Document；Credit Note Owner 不一致或 Version 自引用同样拒绝。
- Notification Callback 未验签或定位不唯一：不推进 Delivery；活动 Complaint Suppression 阻断普通通知。
- Webhook URL 不安全、Subscription Schema 不匹配、旧 Fencing Token 或成功后自动重投：全部拒绝；Replay 只能创建新 Generation。
- Gateway 同 Tier 同 Rule Type 冲突、Item Hash 漂移、Maker=Checker 或实例应用失败：新 Release 不激活，继续使用 Last-Known-Good。
- Config Item 当前 Version 漂移：整批 Release 失败，不允许部分切换。
- MVP 不建设通用字典、用户通知偏好、重复文件 ACL、独立敏感访问链、可编辑任务中心、中央补偿数据库或自动留存/销毁基础设施。
