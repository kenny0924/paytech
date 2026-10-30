#!/usr/bin/env python3
"""多语言发布变更明细一次性补录（technique.md 第 7 节）。

为 change_count 为 NULL 的历史发布生成 t_i18n_release_change 变化行与 change_count。只读语言包对象（公开桶匿名读取，
按 t_i18n_release_bundle.content_hash 校验 SHA-256），不经 Platform、不用 Vault。只用 Python 标准库；数据库经
`docker exec <container> mysql` 访问，密码只从环境变量 MYSQL_PWD 传入容器，不打印。

三个子命令：
  plan    只读：读取全部发布的语言包，计算变化行，写出可审阅的 SQL 文件（不连接写库）。
  verify  只读：从库内明细还原每个发布，与该发布语言包内容逐条比较，必须全部一致；另报告未补录的发布。
  （执行）plan 生成的 SQL 由开发者审阅后手工执行：docker exec -i -e MYSQL_PWD <container> mysql -uroot <db> < plan.sql

SQL 的写入方式：每个发布一个事务，先 `UPDATE t_i18n_release SET change_count = n WHERE release_id = ? AND change_count IS NULL`，
记下 ROW_COUNT()，再 `INSERT … SELECT … FROM (VALUES ROW(…)) WHERE @claimed = 1`；已补录的发布 UPDATE 影响 0 行，不重复写入，
脚本可重复执行。主键按 Foundation SnowflakeIdGenerator 位布局生成（纪元 2025-01-01、region 31、worker 31），
Platform 运行实例为 region 0，不会冲突。
"""
import argparse
import hashlib
import json
import os
import ssl
import subprocess
import sys
import time
import urllib.request

EPOCH_MS = 1735689600000  # 2025-01-01T00:00:00Z
REGION_ID = 31
WORKER_ID = 31
BATCH = 500
BASE_LOCALE = 'en'


# ---------------------------------------------------------------- mysql

def mysql_rows(args, sql):
    """执行只读查询，按 mysql -B 的转义规则还原字段（\\t \\n \\\\ \\0 与 NULL）。"""
    env = dict(os.environ)
    if 'MYSQL_PWD' not in env:
        sys.exit('需要环境变量 MYSQL_PWD')
    out = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', args.container, 'mysql', '-u' + args.user, '-B', '-N',
                          '--default-character-set=utf8mb4', args.db], input=sql.encode(), capture_output=True, env=env)
    if out.returncode != 0:
        sys.exit('mysql 失败: ' + out.stderr.decode(errors='replace').strip())
    rows = []
    for line in out.stdout.decode('utf-8').split('\n'):
        if line == '':
            continue
        rows.append([unescape(field) for field in line.split('\t')])
    return rows


def unescape(field):
    if field == 'NULL':
        return None
    result, i = [], 0
    while i < len(field):
        c = field[i]
        if c == '\\' and i + 1 < len(field):
            n = field[i + 1]
            result.append({'n': '\n', 't': '\t', '\\': '\\', '0': '\0'}.get(n, n))
            i += 2
        else:
            result.append(c)
            i += 1
    return ''.join(result)


def hex_literal(text):
    return "CONVERT(X'" + text.encode('utf-8').hex() + "' USING utf8mb4)"


# ---------------------------------------------------------------- 发布内容

def load_releases(args):
    """应用 → 按发布号升序的发布：release_id、release_no、change_count。"""
    # DDL 执行前（只读试算）没有 change_count 列，全部按未补录处理；生成的 SQL 仍要求先执行 DDL。
    has_column = mysql_rows(args, """
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 't_i18n_release' AND column_name = 'change_count'""")[0][0] == '1'
    rows = mysql_rows(args, """
        SELECT a.application_id, a.application_code, r.release_id, r.release_no, %s
        FROM t_i18n_release r JOIN t_i18n_application a ON a.application_id = r.application_id
        ORDER BY a.application_code, r.release_no""" % ('r.change_count' if has_column else 'NULL'))
    if not has_column:
        print('提示：t_i18n_release.change_count 不存在（DDL 未执行），按全部未补录试算')
    apps = {}
    for app_id, code, release_id, release_no, change_count in rows:
        apps.setdefault((int(app_id), code), []).append(
            {'release_id': int(release_id), 'release_no': int(release_no),
             'change_count': None if change_count is None else int(change_count)})
    for releases in apps.values():
        numbers = [r['release_no'] for r in releases]
        if numbers != list(range(1, len(numbers) + 1)):
            sys.exit('发布号不连续: ' + str(numbers))
    return apps


def load_bundles(args):
    """release_id → [(locale, namespace, bucket, object, content_hash)]。"""
    rows = mysql_rows(args, """
        SELECT b.release_id, b.locale_code, b.namespace_code, o.storage_container_ref, o.storage_object_ref, b.content_hash
        FROM t_i18n_release_bundle b
        JOIN t_file_object o ON o.file_id = CAST(SUBSTRING(b.file_address, 11) AS UNSIGNED)
        ORDER BY b.release_id, b.locale_code, b.namespace_code""")
    bundles = {}
    for release_id, locale, namespace, bucket, obj, content_hash in rows:
        bundles.setdefault(int(release_id), []).append((locale, namespace, bucket, obj, content_hash))
    return bundles


def fetch(args, bucket, obj, content_hash, cache):
    if content_hash in cache:
        return cache[content_hash]
    context = ssl.create_default_context()
    if args.insecure:
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
    url = args.storage_url.rstrip('/') + '/' + bucket + '/' + urllib.request.quote(obj)
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, context=context, timeout=30) as response:
                data = response.read()
            break
        except Exception as failure:  # noqa: BLE001 — 重试后仍失败则停止
            if attempt == 2:
                sys.exit('读取语言包失败: ' + bucket + '/' + obj + ' ' + str(failure))
            time.sleep(1)
    if hashlib.sha256(data).hexdigest() != content_hash:
        sys.exit('语言包内容与登记摘要不一致: ' + bucket + '/' + obj)
    texts = json.loads(data.decode('utf-8'))
    if not isinstance(texts, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in texts.items()):
        sys.exit('语言包不是扁平字符串对象: ' + bucket + '/' + obj)
    cache[content_hash] = texts
    return texts


def release_content(args, bundle_list, cache):
    """语言 → key → 文案（与 ReleaseContent / BundleGenerator 同口径：语言包合并）。"""
    content = {}
    for locale, _namespace, bucket, obj, content_hash in bundle_list:
        target = content.setdefault(locale, {})
        for key, text in fetch(args, bucket, obj, content_hash, cache).items():
            if key in target:
                sys.exit('同一语言出现重复 key: ' + locale + ' ' + key)
            target[key] = text
    return content


def diff(before, after):
    """与 ReleaseDiff.between 同规则：[(locale, key, change_type, text)]，按语言、key 字典序。"""
    changes = []
    for locale in sorted(set(before) | set(after)):
        old_texts, new_texts = before.get(locale, {}), after.get(locale, {})
        for key in sorted(set(old_texts) | set(new_texts)):
            old, new = old_texts.get(key), new_texts.get(key)
            if old is None:
                changes.append((locale, key, 'ADDED', new))
            elif new is None:
                changes.append((locale, key, 'REMOVED', None))
            elif old != new:
                changes.append((locale, key, 'CHANGED', new))
    return changes


# ---------------------------------------------------------------- 主键

class Snowflake:
    def __init__(self):
        self.last, self.sequence = -1, 0

    def next(self):
        now = int(time.time() * 1000)
        if now == self.last:
            self.sequence += 1
            if self.sequence > 4095:
                while now <= self.last:
                    now = int(time.time() * 1000)
                self.sequence = 0
        elif now < self.last:
            sys.exit('时钟回拨，停止')
        else:
            self.sequence = 0
        self.last = now
        return ((now - EPOCH_MS) << 22) | (REGION_ID << 17) | (WORKER_ID << 12) | self.sequence


# ---------------------------------------------------------------- 子命令

def plan(args):
    apps, bundles, cache = load_releases(args), load_bundles(args), {}
    ids = Snowflake()
    lines = ['-- 多语言发布变更明细补录（由 backfill_release_changes.py plan 生成，执行前请审阅）',
             '-- 目标库：' + args.db + '；生成时间 UTC ' + time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
             "SET NAMES utf8mb4;", "SET time_zone = '+00:00';", '']
    summary = []
    for (app_id, code), releases in apps.items():
        previous = {}
        for release in releases:
            current = release_content(args, bundles.get(release['release_id'], []), cache)
            changes = diff(previous, current)
            previous = current
            if release['change_count'] is not None:
                summary.append((code, release['release_no'], len(changes), '已补录，跳过'))
                continue
            summary.append((code, release['release_no'], len(changes), '待写入'))
            lines.append('-- ' + code + ' 发布 ' + str(release['release_no']) + '：' + str(len(changes)) + ' 条变化')
            lines.append('START TRANSACTION;')
            lines.append('UPDATE t_i18n_release SET change_count = ' + str(len(changes)) + ' WHERE release_id = '
                         + str(release['release_id']) + ' AND change_count IS NULL;')
            lines.append('SET @claimed = ROW_COUNT();')
            for start in range(0, len(changes), BATCH):
                values = []
                for locale, key, change_type, text in changes[start:start + BATCH]:
                    values.append('ROW(' + ', '.join([str(ids.next()), str(app_id), str(release['release_no']),
                                                     hex_literal(locale), hex_literal(key), "'" + change_type + "'",
                                                     'NULL' if text is None else hex_literal(text)]) + ')')
                lines.append('INSERT INTO t_i18n_release_change (change_id, application_id, release_no, locale_code, key_code,'
                             ' change_type, text, create_time)')
                lines.append('SELECT v.column_0, v.column_1, v.column_2, v.column_3, v.column_4, v.column_5, v.column_6,'
                             ' UTC_TIMESTAMP(6) FROM (VALUES ' + ',\n  '.join(values) + ') v WHERE @claimed = 1;')
            lines.append('COMMIT;')
            lines.append('')
    with open(args.out, 'w', encoding='utf-8') as handle:
        handle.write('\n'.join(lines) + '\n')
    for row in summary:
        print('%-20s 发布 %3d  变化 %6d  %s' % row)
    print('读取语言包 %d 个（去重后），SQL 已写入 %s' % (len(cache), args.out))


def verify(args):
    apps, bundles, cache = load_releases(args), load_bundles(args), {}
    failures, unrecorded = 0, 0
    for (app_id, code), releases in apps.items():
        for release in releases:
            if release['change_count'] is None:
                unrecorded += 1
                print('%-20s 发布 %3d  未补录' % (code, release['release_no']))
                continue
            expected = release_content(args, bundles.get(release['release_id'], []), cache)
            restored = {}
            for locale, key, text in mysql_rows(args, """
                SELECT locale_code, key_code, text FROM (
                  SELECT locale_code, key_code, change_type, text,
                         ROW_NUMBER() OVER (PARTITION BY locale_code, key_code ORDER BY release_no DESC) AS rn
                  FROM t_i18n_release_change WHERE application_id = %d AND release_no <= %d) latest
                WHERE rn = 1 AND change_type <> 'REMOVED'""" % (app_id, release['release_no'])):
                restored.setdefault(locale, {})[key] = text
            count = int(mysql_rows(args, 'SELECT COUNT(*) FROM t_i18n_release_change WHERE application_id = %d AND release_no = %d'
                                   % (app_id, release['release_no']))[0][0])
            same = restored == expected and count == release['change_count']
            failures += 0 if same else 1
            print('%-20s 发布 %3d  文案 %6d  变化 %6d  %s' % (code, release['release_no'],
                  sum(len(v) for v in expected.values()), count, '一致' if same else '不一致'))
    print('核对完成：不一致 %d 个发布，未补录 %d 个发布' % (failures, unrecorded))
    sys.exit(1 if failures or unrecorded else 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('command', choices=['plan', 'verify'])
    parser.add_argument('--container', required=True, help='MySQL 容器名，如 mor-local-mysql-1')
    parser.add_argument('--db', required=True, help='Platform 库名，如 mor_platform_v2e2e')
    parser.add_argument('--user', default='root')
    parser.add_argument('--storage-url', required=True, help='对象存储地址，如 https://127.0.0.1:19400')
    parser.add_argument('--insecure', action='store_true', help='不校验存储 TLS 证书（本机自签证书）')
    parser.add_argument('--out', help='plan 输出的 SQL 文件')
    args = parser.parse_args()
    if args.command == 'plan':
        if not args.out:
            sys.exit('plan 需要 --out')
        plan(args)
    else:
        verify(args)


if __name__ == '__main__':
    main()
