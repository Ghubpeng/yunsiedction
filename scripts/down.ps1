# 停止完整应用栈（保留数据卷：mysql/redis/minio 数据不丢）
# 用法：powershell -ExecutionPolicy Bypass -File scripts\down.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root "deploy\docker\compose.yml"

Write-Host "==> 停止应用栈（数据卷保留）"
docker compose -f $compose down
Write-Host "==> 已停止；如需彻底清空数据（危险）：docker compose -f deploy\docker\compose.yml down -v"
