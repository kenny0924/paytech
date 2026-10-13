/** 本地开发的登录预填值；只来自开发模式环境变量，生产构建不读取。 */
export interface DevLoginDefaults {
  loginIdentifier?: string;
  password?: string;
}

interface DevLoginEnv {
  MODE?: string;
  VITE_DEV_LOGIN_IDENTIFIER?: string;
  VITE_DEV_LOGIN_PASSWORD?: string;
}

/**
 * 开发模式（vite dev 或 vite build --mode development）下读取 .env.development.local 的账号与密码，用于预填登录表单。
 * 生产模式或未配置时返回空对象；只预填，不自动提交。
 */
export function devLoginDefaults(env: DevLoginEnv): DevLoginDefaults {
  if (env.MODE !== 'development') return {};
  const loginIdentifier = env.VITE_DEV_LOGIN_IDENTIFIER?.trim();
  const password = env.VITE_DEV_LOGIN_PASSWORD;
  return {
    ...(loginIdentifier ? { loginIdentifier } : {}),
    ...(password ? { password } : {}),
  };
}
