# 阶段 5 本机综合验证证据

[验证清单](./verification.json)区分本轮新增验证与阶段 4 复用证据。[HTTP Journal](./http-journal.json)只保留路径、方法和状态，不保留登录正文、Token 或真实环境配置。

本轮临时入口适配器将浏览器请求转到真实 BFF，BFF 再请求真实 Risk；仅 Platform 登录/权限使用现有 HTTP Simulator，导航为显式本机目录 Fixture。故障注入的目录 503 由入口适配器返回，证明 Web 错误处理，不能当作真实 Gateway 故障测试。实测发现的中文查询 503 则来自真实 Risk SQL，已修复。

Journal 保留排查过程：第一次流程 Fixture 使用中文 edgeKey 被 422 正确拒绝；一次无效规则 nodeType 参数被 400 拒绝；未知决定负例曾被已有流程编码/退休规则引用提前阻止，换成合法前置后确认 `VARIABLE_DOMAIN_MISMATCH`。这些是联调 Fixture 修正，不计作产品缺陷。两条真实失败链是中文查询 503、集合保存后旧版本读 409，均已复验。

[harness](./harness/)保存实际使用的临时启动、转发和精确清理源码，供审阅证据边界；其中 `/tmp/rco19-stage5`、端口和本轮专属 ID 仅用于该次联调，不是可直接复制执行的常设环境安装脚本。临时进程已结束，不修改 local-environment 的服务生命周期。原始运行日志仅位于本机 `/tmp`，本目录记录其 Hash。

[清理结果](./cleanup.json)：17 个初始化变量、9 个成员保留；独占变量及规则/流程 Fixture 已清除，5 类执行事实均为 0。没有应用 SQL、修改 test 或提交代码。
