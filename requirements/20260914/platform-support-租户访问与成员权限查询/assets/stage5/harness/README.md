# 租户访问阶段 5 手工本机验收

复用[前序真实链路机制](../../../../../20260913/platform-support-ClientAPI接入管理/assets/stage5/harness/README.md)，按本期缩小到租户访问。执行前需要[精确的本机临时启用确认](../../../db/local-acceptance/README.md)。本目录不是应用启动代码，也不纳入默认auto测试。

生产Web构建→Loopback入口代理→真实Gateway→真实BFF→真实Platform→已准备的独立本机MySQL/Redis。代理只转发并覆盖可信入口Header、记录请求归属，不提供业务响应；Gateway仍执行真实JWT/路由/Guard，Platform提供真实登录/JWKS/Snapshot/PLT-I02及业务结果。开发Vault/签名使用现有auto适配器；BFF/Gateway的test Profile只启用localhost HTTP兼容，不连接mor-test。

## 启动与执行

1. 核对阶段4源文件及各应用Surefire报告classpath，创建仅本次拥有的0700目录`/tmp/plt-tenant-access-stage5`。私有账号和临时令牌不提交、不输出到日志。
2. 已获本机确认后，先运行`node <本目录>/web.mjs`，再在Shell载入local-environment/.env.local（不得打印）后运行`python3 <本目录>/start.py platform`。等待platform.json出现；此时只创建合成夹具，正式登记仍禁用。
3. 手工执行db/local-acceptance/enable.sql，单连接核对8/3后COMMIT。启动器不应用登记或启用SQL。
4. 依次执行`python3 <本目录>/start.py bff`、`... gateway`，等待各私有runtime JSON；新进程全部随机Loopback监听。
5. 执行`python3 <本目录>/verify.py`，通过真实入口检查八项能力、权限隔离、分页、PLT-I03、幂等/审计和失败关闭，再用浏览器完成页面操作。
6. 先停止入口/Gateway/BFF，正常终止Platform，等待自身夹具清理。手工执行restore.sql并核对目录恢复及无合成残留。禁止用SIGKILL跳过正常清理；异常情况先核对精确归属。

## 合成场景与边界

- 4个本次Operations账号：管理、只读租户、只读成员、无本期权限；权限只绑定自有临时角色，原管理员关联不变。
- 7个Client Tenant、6个Partner Tenant，其中6个CUSTOMER账号分别加入同组Client与Partner；最后一个Client无成员，共13条租户用于分页。
- 首个成员4条角色授予、13个权限：有效、未来、过期、撤销来源同时存在，一条权限停用；第二个Client来源与安全均停用；第三组账号/成员停用。
- `/fixture-state`是仅在随机Loopback控制口开放的手工场景入口，只能临时破坏/恢复首个自有角色的归属，用于验证页面服务失败；不经过公开代理，不加入生产应用。
- `verify.py`操作真实业务命令，但不会模拟CUSTOMER登录、CCT投影或外部Client业务执行。浏览器/API结果另行归档，准备或编译成功不等于验收通过。
