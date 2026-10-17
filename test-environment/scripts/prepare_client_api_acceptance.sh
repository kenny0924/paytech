#!/usr/bin/env bash
# Client API 联通验收前置（2026-10-17）：
#   1. 用 test CA 签发 webhook-receiver 服务端证书（SAN=webhook-receiver），并建接收目录；
#   2. 生成 Platform JVM 信任库 = Temurin 镜像自带 cacerts + test CA（保留公共 CA，SES 等出站不受影响）。
# 产物都在 private/（不入库）；已存在则不覆盖，传 --force 重新生成。不打印私钥或口令。
set -euo pipefail
environment_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
private_dir="$environment_dir/private"
receiver_dir="$private_dir/webhook-receiver"
truststore="$private_dir/platform-truststore.p12"
java_image="eclipse-temurin:25.0.4_7-jre-noble"
force=false
[[ "${1:-}" == "--force" ]] && force=true

for required in "$private_dir/test-ca.crt" "$private_dir/test-ca.key"; do
  [[ -f "$required" ]] || { echo "缺少 $required，先运行 scripts/prepare.py" >&2; exit 1; }
done

mkdir -p "$receiver_dir/inbox"
chmod 0700 "$receiver_dir"
# 接收端容器以 10001 运行，需要写入 inbox。
chmod 0777 "$receiver_dir/inbox"

if [[ "$force" == true || ! -f "$receiver_dir/tls.crt" ]]; then
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' EXIT
  openssl req -new -newkey rsa:2048 -nodes -subj "/CN=webhook-receiver" \
    -keyout "$receiver_dir/tls.key" -out "$work/receiver.csr" 2>/dev/null
  printf 'subjectAltName=DNS:webhook-receiver\nextendedKeyUsage=serverAuth\nkeyUsage=digitalSignature,keyEncipherment\n' > "$work/ext.cnf"
  openssl x509 -req -in "$work/receiver.csr" -CA "$private_dir/test-ca.crt" -CAkey "$private_dir/test-ca.key" \
    -CAserial "$private_dir/test-ca.srl" -days 397 -sha256 -extfile "$work/ext.cnf" -out "$receiver_dir/tls.crt" 2>/dev/null
  # 容器用户 10001 需读取私钥；目录本身 0700 限制宿主机其他用户。
  chmod 0644 "$receiver_dir/tls.crt" "$receiver_dir/tls.key"
  openssl verify -CAfile "$private_dir/test-ca.crt" "$receiver_dir/tls.crt" >/dev/null
  echo "webhook-receiver 证书已签发"
fi

if [[ "$force" == true || ! -f "$truststore" ]]; then
  docker run --rm --user "$(id -u):$(id -g)" -v "$private_dir:/work" --entrypoint /bin/sh "$java_image" -ec '
    keytool -importkeystore -noprompt -srckeystore "$JAVA_HOME/lib/security/cacerts" -srcstorepass changeit \
      -destkeystore /work/platform-truststore.p12 -deststoretype PKCS12 -deststorepass changeit >/dev/null 2>&1
    keytool -importcert -noprompt -alias mor-test-ca -file /work/test-ca.crt \
      -keystore /work/platform-truststore.p12 -storetype PKCS12 -storepass changeit >/dev/null 2>&1'
  chmod 0644 "$truststore"
  echo "Platform 信任库已生成（cacerts + test CA）"
fi
