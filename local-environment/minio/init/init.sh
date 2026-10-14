#!/bin/sh
# MinIO 初始化（本机与 mor-test 共用，test 以 MINIO_URL 指定地址）：两个版本化、SSE-S3 加密的存储桶及仅限这两个桶的 Platform 账号。可重复执行。
set -eu
mc() { /usr/bin/mc --insecure "$@"; }
mc alias set local "${MINIO_URL:-https://minio:9000}" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
for bucket in mor-platform-files mor-platform-files-test; do
  mc mb --ignore-existing "local/$bucket"
  mc version enable "local/$bucket"
  mc encrypt set sse-s3 "local/$bucket"
done
cat > /tmp/platform-files.json <<'POLICY'
{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":["s3:GetBucketLocation","s3:ListBucket","s3:ListBucketVersions","s3:GetBucketVersioning"],
  "Resource":["arn:aws:s3:::mor-platform-files","arn:aws:s3:::mor-platform-files-test"]},
 {"Effect":"Allow","Action":["s3:PutObject","s3:GetObject","s3:GetObjectVersion","s3:DeleteObject","s3:DeleteObjectVersion"],
  "Resource":["arn:aws:s3:::mor-platform-files/*","arn:aws:s3:::mor-platform-files-test/*"]}]}
POLICY
mc admin policy create local platform-files /tmp/platform-files.json
mc admin user add local "$PLATFORM_ACCESS_KEY" "$PLATFORM_SECRET_KEY"
mc admin policy attach local platform-files --user "$PLATFORM_ACCESS_KEY" 2>/dev/null || true
echo "minio-init: buckets and platform-files user ready"
