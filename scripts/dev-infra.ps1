# 启动本地开发基础设施（MySQL / Redis / MinIO）
# 用法：powershell -ExecutionPolicy Bypass -File scripts\dev-infra.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root "deploy\docker\compose.yml"

docker compose -f $compose up -d mysql redis minio
docker compose -f $compose ps
