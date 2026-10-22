"""回归对照：以统一对外渠道出口第二段的 stage5p2-after.json（上一版本）为基线，对照本次 stage5-regression-after.json。
复用 20261020 verification/stage5p2_compare.py 的比较逻辑（不改原文件）：只替换输入文件与输出位置。"""
import json, sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
PREV = HERE.parents[2] / "20261020/channel-connector-统一对外渠道出口/verification"
sys.path.insert(0, str(PREV))
import stage5p2_compare as c  # noqa: E402
files = {"stage5p2-baseline.json": PREV / "stage5p2-after.json", "stage5p2-after.json": HERE / "stage5-regression-after.json"}
c.load = lambda name: json.loads(files[name].read_text())
c.HERE = HERE / "_compare_tmp"
c.HERE.mkdir(exist_ok=True)
c.main()
(HERE / "_compare_tmp/stage5p2-compare.json").rename(HERE / "stage5-regression-compare.json")
c.HERE.rmdir()
