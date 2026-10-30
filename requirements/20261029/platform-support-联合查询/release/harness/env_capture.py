"""只读记录 mor-test 容器 env（键与非密值）。密钥类键整值替换；复合值（如 JAVA_TOOL_OPTIONS、SPRING_APPLICATION_JSON）中
形如 *password* / *secret* / *token* / *key* = 值 或 "…": "值" 的片段也替换为 <redacted>。用法：python3 -I env_capture.py <输出> <服务>…"""
import json, re, subprocess, sys
SEC_KEY = re.compile(r"(PASSWORD|PASSWD|PWD|SECRET|TOKEN|PRIVATE|CREDENTIAL|_KEY$|_KEY_|HMAC|SIGNING|ACCESSKEY|ACCESS_KEY)", re.I)
INNER = re.compile(r"(?i)([\w.\-]*(password|passwd|pwd|secret|token|apikey|api_key|accesskey|private[_-]?key|credential)[\w.\-]*)"
                   r"(\s*[=:]\s*\"?)([^\s\",}]+)")
def redact(key, value):
    return "<redacted>" if SEC_KEY.search(key) else INNER.sub(lambda m: m[1] + m[3] + "<redacted>", value)
out = []
for svc in sys.argv[2:]:
    env = json.loads(subprocess.check_output(["docker", "inspect", f"mor-test-{svc}-1", "--format", "{{json .Config.Env}}"]))
    out.append(f"## {svc} ({len(env)} keys)")
    out += [f"{k}={redact(k, v)}" for k, v in (e.split("=", 1) for e in sorted(env))]
open(sys.argv[1], "w").write("\n".join(out) + "\n")
