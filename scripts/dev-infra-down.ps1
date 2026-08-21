# 停止本地开发基础设施（保留数据卷；如需连数据一起清理，使用 down -v）
# 用法：powershell -ExecutionPolicy Bypass -File scripts\dev-infra-down.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root "deploy\docker\compose.yml"

docker compose -f $compose down
