package base

// mockConfig 是自动测试的完整配置清单（对应 Java application-mock.yml）：列出 internal/config 读取的全部键。
//
// 动态值来源：VAULT_DB_DSN ← 环境变量 VAULT_TEST_DB_DSN；VAULT_POLICY_FILE ← 本次生成的临时策略；
// VAULT_LOCAL_AES_ROOT_KEY ← 本次随机生成。其余为固定值。
func mockConfig(dsn, policyFile, rootKey string) map[string]string {
	return map[string]string{
		// 运行模式：test 允许 LOCAL AES；production 会拒绝启动。
		"VAULT_RUNTIME_MODE": "test",
		// 必须是合法地址；测试不监听该端口，由 httptest 承载同一 Handler。
		"VAULT_HTTP_ADDRESS": "127.0.0.1:19127",
		"VAULT_DB_DSN":       dsn,
		"VAULT_POLICY_FILE":  policyFile,
		// LOCAL AES 根密钥（base64 的 32 字节）与非敏感密钥引用。
		"VAULT_LOCAL_AES_ROOT_KEY": rootKey,
		"VAULT_LOCAL_AES_KEY_ID":   "auto-test",
		// Snowflake 坐标：31/27 专属本测试模块（31/28 黑盒、31/29～31 内部包测试）。
		"VAULT_SNOWFLAKE_DATACENTER_ID": "31",
		"VAULT_SNOWFLAKE_WORKER_ID":     "27",
		// 自动测试不连接 Nacos；以下注册参数仅在启用时读取，此处列出完整键名。
		"VAULT_NACOS_ENABLED":        "false",
		"VAULT_NACOS_SERVER_ADDRESS": "",
		"VAULT_NACOS_NAMESPACE":      "",
		"VAULT_NACOS_GROUP":          "",
		"VAULT_NACOS_INSTANCE_IP":    "",
		"VAULT_NACOS_INSTANCE_PORT":  "",
		"VAULT_NACOS_USERNAME":       "",
		"VAULT_NACOS_PASSWORD":       "",
	}
}
