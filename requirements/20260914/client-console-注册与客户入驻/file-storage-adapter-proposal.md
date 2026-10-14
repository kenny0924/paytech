# 入驻文件存储与检查适配器（确认稿）

> 状态：2026-10-14 用户确认（“可以”），含第 5 节两项。归属阶段 4 研发；不包含部署、生产 AWS 资源配置或公共入口启用。上传业务链（F01—F04、C12、扫描补偿）沿用[文件合同补充](./file-upload-closure-proposal.md)，本稿只补齐其缺失的出站适配器。

## 1. 用户决定

| 日期 | 决定 | 原话 |
|---|---|---|
| 2026-10-14 | 存储做 MinIO 与 AWS S3 两个实现，按配置选择；本机用 MinIO，生产用 S3 | “做2个实现，一个MinIO存储实现，一个AWS的S3的存储实现，通过配置来决定当前环境使用哪个存储实现” |
| 2026-10-14 | 暂不做病毒扫描 | “病毒扫描暂时不用吧，本机也是用来测试的” |

## 2. 现状与缺口

Platform 已定义 `UploadStorage`（签发直传授权）、`CompletedUploadStorage`（固定并读取准确版本）、`FileDownloadStorage`（签发短时下载授权）与 `FileScanner` 四个端口，均无实现；`t_file_scan_policy`/`t_file_scan_requirement` 无数据。扫描策略没有检查项时 `beginScan` 明确返回不可用，不会自动放行，因此“不扫毒”仍需要一个真实检查项。

## 3. 存储适配器

- 位置：`platform-support-file-document` 的 `integration/storage/minio` 与 `integration/storage/s3`。MinIO 使用 MinIO Java SDK，S3 使用 AWS SDK v2（`s3` + 预签名），SDK 只出现在这两个包内；版本登记到技术栈文档。
- 选择：`files.onboarding.storage-provider` 为 `MINIO` 或 `S3`，对应实现按 `@ConditionalOnProperty` 唯一装配；其余值或缺失时启动失败。每个实现同时提供上传、完成读取、下载和新增的“按文件读取准确版本”能力，`providerCode()` 与配置一致。
- 一次写入：存储桶必须开启版本控制；上传授权为预签名 PUT，附带 `If-None-Match: *`，对象已存在时拒绝写入；授权有效期不晚于 `Upload.expiresAt`，地址必须为 HTTPS。
- 完成读取：读取对象当前唯一版本的 `versionId`，后续读取一律按该版本；版本缺失或为 `null` 视为不可用。媒体类型由读取到的文件头识别（PDF、JPEG、PNG），不使用浏览器声明或 ETag。加密引用取存储返回的服务端加密信息（S3：SSE-KMS 密钥 ARN；MinIO：SSE-S3 单密钥），缺失则不可用。
- 下载：签发原对象原版本的预签名 GET，有效期不超过调用方给出的期限（最长 60 秒），不返回存储凭据。
- 配置：连接地址（S3 用区域，可选终端）、凭据来源（S3 走默认凭据链，MinIO 走本地密钥）、公开 HTTPS 基址、超时上限。凭据不进入日志或数据库。
- 本机 HTTPS：MinIO 容器只监听 loopback `127.0.0.1:19400`，使用 `local-environment/minio/generate-certs.sh` 生成的自签名证书直接提供 HTTPS（实施时由原定 Nginx 同源改为直连，避免签名 Host 改写）；浏览器需信任该证书。社区版官方镜像已下架，改用 Chainguard 源码构建镜像并按摘要固定。

## 4. 文件检查（替代病毒扫描）

新增 Platform 内置 `FORMAT` 检查器 `PLATFORM_FORMAT`：按准确版本重新读取字节，复核 SHA-256 与大小等于登记值，并以文件头复核媒体类型属于策略 `allowed_mime_rules`；通过返回 `FORMAT_VALID`，否则 `FORMAT_INVALID`。这是真实检查，不是默认放行。

本机扫描策略只配置这一项 `FORMAT` 要求；生产启用前必须另行增加 `ANTI_MALWARE` 要求与真实扫描器，本稿不预先批准生产只做格式检查。

## 5. 待确认

1. 运营审核详情页查看材料：需 Platform 新增运营侧文件读取接口（PLT-I02 授权后签发短时下载），BFF 转发，页面提供预览/下载。建议本轮一起做。
2. 本机环境改动（另行授权）：`local-environment` 新增 MinIO 容器；向本机 `mor_platform` 写入一条 `t_file_scan_policy` 与一条 `t_file_scan_requirement`。

## 6. 测试

MinIO 实现以本机 MinIO 容器跑 `auto` 测试，覆盖上传、重复写拒绝、版本固定、类型识别、下载签名与不可用分支；S3 实现在自动测试中只验证配置装配与签名请求形状，不连接真实 AWS，真实 S3 验收列为生产启用门禁。格式检查器覆盖通过、类型不符、摘要或大小不符与读取失败。

## 7. 实施补充（2026-10-14）

- 完成读取除“对象不存在才写入”外，再要求对象恰好只有一个版本；被重复写入的对象视为不可用，防止迟到 PUT 改变已固定版本。
- O10：`POST /client-onboarding-admin/v1/cases/{caseId}/files/{fileId}/download`（权限 `CLIENT_ONBOARDING_REVIEW_READ`）。BFF 先读 Risk 任务确认文件属于该任务证据，再调用 Platform `POST /review-files/v1/files/{fileId}/download`；Platform 重检运营会话与“审核查看/材料核验”权限之一、文件可用状态，签发最长 60 秒原版本地址并记审计。注册计划增加 `client-onboarding-o10`（共 39 条路由）。
- 本机扫描策略 `CLIENT_ONBOARDING_FORMAT` 已按[执行记录](./db/implementation/platform-file-scan-policy-local-execution.md)写入。
- 生产 S3 存储桶需开启版本控制、默认 SSE-KMS，并配置允许 Client Console 源的 CORS（PUT 携带 Content-Type、If-None-Match）。
