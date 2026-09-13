# 本期本机整链路验收启动器

仅手工验收使用，不进入生产应用。当前验收结果由根 CURRENT_IMPLEMENTATION.md 维护；本目录只保存可复现的执行机制。

链路为生产 Web 构建 → 本机 HTTP 入口代理 → 真实 Gateway → 真实 BFF → 真实 Platform → local-environment MySQL/Redis。入口代理模拟 Nginx 覆盖单一 X-Real-IP 和 X-Entry-Channel，不代替鉴权或业务响应。Operations Request ID 由真实 Gateway 重新生成并沿 BFF/Platform 传递。真实 Platform 提供 Snapshot、JWKS、登录与授权。服务发现使用本机 SimpleDiscovery 地址；Platform 的开发签名/Email Vault 复用已批准的 auto 适配器，BFF/Gateway 单独启用 test Profile 以满足 localhost HTTP 的现有边界（不代表连接共享 test 环境）。

PlatformAcceptance 只允许 `127.0.0.1:13306/mor_platform_config_validation`，先用现有独立 Oracle 校验完整目录，使用既有 OperationsFixtureMocker 写入合成身份、临时角色、签名 Key 和独立审计链。仅将本期 18 个 Route / 4 个 Resource 的初始 DISABLED 状态临时改为 ACTIVE，管理员原 REVOKED 授权保持原值。退出时清理自有记录并恢复初始状态，不创建 Schema、不修改旧库、不删除验证库。

`/stage5-observed-owner` 是专用于证明新 Target/Route 经真实数据库快照转发的合成 HTTP 下游，不承担任何 Platform/BFF 业务，不冒充其他 Channel 的真实业务认证。

运行前先完成应用构建/测试，在仓库根目录通过 Shell 载入本机私有环境（不要输出内容）；Java 由现有 JDK 25 运行。三个 Java 启动器分别使用对应服务最后一次 Surefire 报告中的实际 classpath，因此不得在运行期间清理或重建这些服务。

1. 确保 `/tmp/plt-config-stage5` 是本任务创建的权限 700 目录。
2. 运行 `node <本目录>/web.mjs`，生成 Web 随机 Loopback Origin。
3. 依次运行 `python3 <本目录>/start.py platform`、`... bff`、`... gateway`；每次等待对应本机 runtime JSON 生成后再启动下一个。
4. 运行 `python3 <本目录>/verify.py`，通过真实公开入口执行代表性端点与权限分支。再用浏览器执行页面验收；新增数据编码先通过本机 Platform 控制口 `/fixture?RESOURCE` 等登记归属。
5. 停止 Web、Gateway、BFF，再向 Platform 发送正常终止信号以触发清理；核对目录恢复、合成账号/会话/审计/命令归零。不要用 SIGKILL 绕过清理。

运行文件中可能包含仅本次合成账号和临时令牌；保存在本机私有目录，不归档到仓库。HTTP 私有记录保存网关实际 Request ID 及合成命令 Key，用于精确清理对应审计与命令；每次启动 Platform 清空旧记录。Platform 关闭 Spring 自动 shutdown hook，由手工启动器先清理夹具再关闭连接池，避免退出竞态。长期只保留去除凭据的检查摘要、源码 Hash、页面证据与清理结果。
