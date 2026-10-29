"""替换本机 e2e 共享栈中正在运行的 JVM（memory: local-e2e-jar-swap）：
  1) 抓取在运行进程的 argv 与环境（ps eww），只保存在 scratchpad 的 700 目录，不打印私密值；
  2) 首次替换时把旧 JAR 备份为 <jar>.pre-rq03（cp -p）；新 JAR 先复制为临时文件再 rename（新 inode，不改写运行中的文件）；
  3) 停止旧进程，按抓取的 argv / 环境（可追加覆盖项）重启，写 PID 与 .started，等待 readiness。
用法：
  python3 swap.py capture <name> <pid>
  python3 swap.py install <name> <new-jar>             只做备份与安装（不重启）
  python3 swap.py restart <name> [KEY=VALUE ...]       用上次抓取的 argv / 环境重启（覆盖项追加到环境）
"""
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

E2E = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e")
LOGS = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/logs")
CAPTURE = Path("/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/3e08b044-fee7-45d0-ba22-072a21cafc69/scratchpad/rq03-e2e/proc")
JAR = {"client-master": "paytech-client-master-app-exec.jar", "compliance": "paytech-compliance-app-exec.jar",
       "ops-bff": "paytech-operations-console-bff-app-exec.jar", "platform": "paytech-platform-support-app-exec.jar",
       "client-bff": "paytech-client-console-bff-app-exec.jar", "commercial": "paytech-commercial-app-exec.jar",
       "risk-decision": "paytech-risk-decision-app-exec.jar"}
READY = {"client-master": "http://127.0.0.1:19101/actuator/health/readiness",
         "compliance": "http://127.0.0.1:19112/actuator/health/readiness",
         "ops-bff": "http://127.0.0.1:19201/actuator/health/readiness",
         "platform": "http://127.0.0.1:18106/platform-support/actuator/health/readiness",
         "client-bff": "http://127.0.0.1:19202/actuator/health/readiness",
         "commercial": "http://127.0.0.1:19108/actuator/health/readiness",
         "risk-decision": "http://127.0.0.1:19104/actuator/health/readiness"}


def capture(name, pid):
    CAPTURE.mkdir(parents=True, exist_ok=True)
    os.chmod(CAPTURE, 0o700)
    argv = subprocess.run(["ps", "-ww", "-o", "command=", "-p", pid], capture_output=True, text=True, check=True).stdout.strip()
    full = subprocess.run(["ps", "eww", "-o", "command=", "-p", pid], capture_output=True, text=True, check=True).stdout.strip()
    if not full.startswith(argv):
        raise SystemExit("ps 输出不一致，停止")
    (CAPTURE / f"{name}.argv").write_text(argv)
    (CAPTURE / f"{name}.env").write_text(full[len(argv):].strip())
    (CAPTURE / f"{name}.pid").write_text(pid)
    for f in CAPTURE.glob(f"{name}.*"):
        os.chmod(f, 0o600)
    print(f"captured {name} pid {pid}: {len(argv.split())} argv tokens, {len(parse_env(full[len(argv):]))} env vars")


def parse_env(text):
    env = {}
    for part in re.split(r"\s(?=[A-Za-z_][A-Za-z0-9_]*=)", " " + text.strip()):
        part = part.strip()
        if "=" in part:
            key, value = part.split("=", 1)
            env[key] = value
    return env


def install(name, new_jar):
    target = E2E / "jars" / JAR[name]
    backup = target.with_name(target.name + ".pre-rq03")
    if not backup.exists():
        subprocess.run(["cp", "-p", str(target), str(backup)], check=True)
        print(f"backup {backup.name}")
    tmp = target.with_name("." + target.name + ".tmp")
    shutil.copyfile(new_jar, tmp)
    os.replace(tmp, target)
    print(f"installed {target.name} ({target.stat().st_size} bytes)")


def restart(name, overrides):
    argv = (CAPTURE / f"{name}.argv").read_text().split()
    env = parse_env((CAPTURE / f"{name}.env").read_text())
    for item in overrides:
        key, value = item.split("=", 1)
        env[key] = value
    pid = (CAPTURE / f"{name}.pid").read_text().strip()
    current = (E2E / f"{name}.pid").read_text().strip() if (E2E / f"{name}.pid").exists() else pid
    for candidate in {pid, current}:
        if alive(candidate) and JAR[name] in cmdline(candidate):
            os.kill(int(candidate), 15)
            for _ in range(40):
                if not alive(candidate):
                    break
                time.sleep(1)
            if alive(candidate):
                os.kill(int(candidate), 9)
            print(f"stopped {name} pid {candidate}")
    started = time.time()
    (E2E / f"{name}.started").write_text(str(int(started)))
    log = open(LOGS / f"{name}.log", "ab")
    log.write(f"\n===== rq03-stage5 restart {time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())} =====\n".encode())
    log.flush()
    process = subprocess.Popen(argv, env=env, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, start_new_session=True,
                               cwd=env.get("PWD") if os.path.isdir(env.get("PWD", "")) else str(E2E))
    (E2E / f"{name}.pid").write_text(str(process.pid))
    (CAPTURE / f"{name}.pid").write_text(str(process.pid))
    print(f"started {name} pid {process.pid} overrides={[o.split('=')[0] for o in overrides]}")
    for _ in range(120):
        if process.poll() is not None:
            raise SystemExit(f"{name} exited with {process.returncode}; see {LOGS / (name + '.log')}")
        try:
            with urllib.request.urlopen(READY[name], timeout=2) as response:
                if b'"UP"' in response.read():
                    print(f"{name} ready in {int(time.time() - started)}s")
                    return
        except Exception:
            pass
        time.sleep(2)
    raise SystemExit(f"{name} not ready in time")


def alive(pid):
    try:
        os.kill(int(pid), 0)
        return True
    except (OSError, ValueError):
        return False


def cmdline(pid):
    return subprocess.run(["ps", "-ww", "-o", "command=", "-p", str(pid)], capture_output=True, text=True).stdout


if __name__ == "__main__":
    command, name = sys.argv[1], sys.argv[2]
    if command == "capture":
        capture(name, sys.argv[3])
    elif command == "install":
        install(name, sys.argv[3])
    elif command == "restart":
        restart(name, sys.argv[3:])
    else:
        raise SystemExit(__doc__)
