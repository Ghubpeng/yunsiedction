# 一键启动完整应用栈（基础设施 + server + web）
# 首次使用：复制 deploy\docker\.env.example -> deploy\docker\.env
# 用法：powershell -ExecutionPolicy Bypass -File scripts\up.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root "deploy\docker\compose.yml"
$envFile = Join-Path $root "deploy\docker\.env"

if (-not (Test-Path $envFile)) {
    Write-Host "==> 未发现 deploy\docker\.env，从 .env.example 复制（开发默认值）"
    Copy-Item (Join-Path $root "deploy\docker\.env.example") $envFile
}

Write-Host "==> 启动完整应用栈（依赖未就绪前 server 不启动；Flyway 由应用执行）"
docker compose -f $compose up -d
if ($LASTEXITCODE -ne 0) { throw "docker compose up 失败" }

Write-Host "==> 等待服务健康（约 1~3 分钟，首次需构建/拉取镜像）"
& (Join-Path $root "scripts\health.ps1")
