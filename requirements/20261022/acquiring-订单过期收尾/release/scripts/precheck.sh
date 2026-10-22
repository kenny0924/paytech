#!/usr/bin/env bash
# acquiring 订单过期收尾 · mor-test 只读预检包装：执行 00-precheck.sql 并把结果写到 stdout（不含任何口令）。
# 用法：requirements/20261022/acquiring-订单过期收尾/release/scripts/precheck.sh > <输出文件>
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../../../../.." && pwd)"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t \
  < "$here/00-precheck.sql"
