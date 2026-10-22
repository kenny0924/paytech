"""阶段 5 包装：本机运营入口为自签名证书（https://127.0.0.1:18444），在导入 configure_3ds 之前关闭 urllib 默认证书校验后原样运行。
用法：E2E_RUNTIME=<含 platform.json / web.json 的私有目录> python3 run_configure_3ds.py PAYERMAX_MOCK"""
import runpy, ssl, sys
from pathlib import Path
ssl._create_default_https_context = ssl._create_unverified_context  # 只连接 127.0.0.1
script = Path(__file__).resolve().parents[2] / 'assets/harness/configure_3ds.py'
sys.argv = [str(script)] + sys.argv[1:]
runpy.run_path(str(script), run_name='__main__')
