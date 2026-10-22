# 阶段 6 卡 F：以 main `73a2460d` 重建两项需求的集成代码

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-card3ds-rel`（已建好，detached at `73a2460d`）。只在这里改代码。
- 输入补丁：`/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/936397ef-ca1e-4532-b001-b27dfa1be0a4/scratchpad/patches/final-int.patch`（相对 `1d39cd94` 的完整改动，含阶段 5 的 S5-01、S5-02 修复；不含 node_modules 软链接）。
- 参考：上一轮合并目录 `/Users/kenny/Desktop/Workspace/Code-MoR-card3ds-int`（只读，阶段 5 验收通过的版本）；公共规则 [00-common.md](./00-common.md)。
- `73a2460d` 相对 `1d39cd94` 多了：CYBS 3 个脚本函数（`3a4574c7`）、文件多存储第二段（`73a2460d`：Platform 删除 Owner 与入驻文件入口，新增 `FileAddress` / `FileErrorCode` / `FileDisposition`，内部文件接口响应带 `fileAddress`，Platform internal-auth DUAL；client-master、client-console-bff、ops-bff、工作流适配）。
- 报告：`tasks/F-rebase-report.md`

## 步骤

1. `git apply --3way --index` 输入补丁，逐个解决冲突。原则：两边都是追加的保留两边；生成文件（PDManer 模型、`table-ownership.yaml`）用生成器重生成；表数断言按实际重算。
2. **与第二段的三处衔接（必须处理）：**
   - Platform CSV 识别：本需求改了 `StoredContentType`、`CompletedUploadVerifier`、`FormatFileScanner`；按第二段之后的新代码重新落地同样的行为（`text/csv`：无 PDF/PNG/JPEG 文件头、开头抽样合法 UTF-8、扫描时全文合法 UTF-8 且无 NUL），沿用第二段的错误码类型。
   - ops-bff 调 Platform 内部文件接口的 `X-Caller-Service`：第二段已经让 Platform 客户端统一带调用方头；本需求的 BIN 上传改为复用第二段的客户端与头配置，删除本需求自加的只对文件客户端生效的头配置（不要再出现两套）。
   - BFF 自拼文件地址 `/files/v1/{fileId}`：改为使用第二段内部文件接口完成上传响应里的 `fileAddress`；B7 读扫描状态的额外 `getFile` 调用若第二段响应已带扫描状态则删除，否则保留并在报告说明。
   - Card 调 Platform 内部文件接口（`getFile`、下载地址）：按 DUAL 带 `X-Caller-Service: card-service`，确认第二段的接口合同与 Card 客户端一致（`fileAddress`、错误码）。
3. 全 reactor `test-compile`。
4. 测试（只跑受影响模块；Platform 测试使用本机 `mor_platform` 前，先给主会话（SendMessage team-lead）发消息申请窗口，收到同意后再跑）：platform-support、card、ops-bff、payment、channel-connector、risk-decision、acquiring、counterparty、commercial、api-gateway、arch-rules、messaging、client-master（第二段改过，确认未被本需求破坏）；PDManer node 测试；ops-web `npm test` / `tsc` / `build`（node_modules 用指向主工作区的临时软链接，结束后删除）。
5. 不提交、不 install 到 `~/.m3`、不碰 mor-test 与本机 e2e。

## 报告

冲突清单与解决方式、三处衔接的具体改动、测试命令与结果（与上一轮对照）、偏离与裁决。最后回复 ≤15 行摘要。
