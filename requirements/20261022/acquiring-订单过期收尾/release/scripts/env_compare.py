#!/usr/bin/env python3
"""acquiring 订单过期收尾 · mor-test 只读环境核对（阶段 6 编写；阶段 7 S1 / 每步替换后复用）。

对给定服务：用 compose.sh 渲染 [覆盖文件] 的最终环境（不传覆盖文件即只渲染主 compose），与运行容器 printenv 逐键比较。
只输出键名与比较结论（SAME / DIFF / ONLY_COMPOSE / ONLY_CONTAINER），任何值都不输出、不落盘。
容器侧只比较 compose 渲染出的键；镜像自带键（PATH、JAVA_HOME 等）列为 ONLY_CONTAINER 供人工判断。

用法：python3 -I env_compare.py [--override <compose 覆盖文件>] <service> [<service> ...]
"""
import json, os, subprocess, sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), *[".."] * 5))
COMPOSE_SH = os.path.join(REPO, "test-environment", "compose.sh")
IMAGE_KEYS = {"PATH", "HOSTNAME", "HOME", "JAVA_HOME", "LANG", "LANGUAGE", "LC_ALL", "JAVA_VERSION", "TZ"}


def rendered(override, services):
    cmd = [COMPOSE_SH] + (["-f", os.path.abspath(override)] if override else []) + ["config", "--format", "json"] + services
    out = subprocess.run(cmd, check=True, capture_output=True, text=True).stdout
    doc = json.loads(out)
    return {s: {k: ("" if v is None else str(v)) for k, v in (doc["services"][s].get("environment") or {}).items()}
            for s in services}, {s: doc["services"][s].get("image") for s in services}


def live(service):
    out = subprocess.run(["docker", "exec", f"mor-test-{service}-1", "printenv"], check=True, capture_output=True, text=True).stdout
    env = {}
    for line in out.splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            env[k] = v
    return env


def main(argv):
    override = None
    if len(argv) >= 2 and argv[0] == "--override":
        override, argv = argv[1], argv[2:]
    if not argv:
        print(__doc__)
        return 2
    envs, images = rendered(override, argv)
    worst = 0
    for s in argv:
        want, have = envs[s], live(s)
        print(f"== {s}  compose 键 {len(want)}  容器键 {len(have)}  渲染镜像 {images[s]}")
        for k in sorted(set(want) | set(have)):
            if k in want and k in have:
                state = "SAME" if want[k] == have[k] else "DIFF"
            elif k in want:
                state = "ONLY_COMPOSE"
            else:
                state = "ONLY_CONTAINER" + ("(image)" if k in IMAGE_KEYS else "")
            if state != "SAME":
                print(f"   {state:24} {k}")
                if state in ("DIFF", "ONLY_COMPOSE") or state == "ONLY_CONTAINER":
                    worst = 1
        same = sum(1 for k in want if k in have and want[k] == have[k])
        print(f"   SAME {same} / {len(want)}")
    return worst


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
