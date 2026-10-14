#!/bin/sh
# 生成仅本机使用的自签名 TLS 证书（localhost / 127.0.0.1 / minio），有效期 825 天；私钥不入库。
set -eu
dir="$(cd "$(dirname "$0")" && pwd)/certs"
mkdir -p "$dir"
if [ -f "$dir/public.crt" ]; then echo "certs already exist: $dir"; exit 0; fi
openssl req -x509 -newkey rsa:3072 -sha256 -days 825 -nodes \
  -keyout "$dir/private.key" -out "$dir/public.crt" -subj "/CN=mor-local-minio" \
  -addext "subjectAltName=DNS:localhost,DNS:minio,IP:127.0.0.1" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,digitalSignature,keyCertSign" 2>/dev/null
chmod 644 "$dir/private.key" "$dir/public.crt"
echo "generated $dir/public.crt"
