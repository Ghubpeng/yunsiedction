# 一键构建应用镜像（server + web）
# 用法：powershell -ExecutionPolicy Bypass -File scripts\build.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root "deploy\docker\compose.yml"

Write-Host "==> 构建应用镜像 (server / web)"
docker compose -f $compose build server web
if ($LASTEXITCODE -ne 0) { throw "docker compose build 失败" }

Write-Host "==> 构建完成"
docker images --format "{{.Repository}}:{{.Tag}}  {{.Size}}" | Select-String "yunsie-server:local|yunsie-web:local"
