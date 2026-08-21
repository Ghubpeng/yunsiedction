# 健康检查：web / server health / mysql / redis / minio / /api/v1 反代链路
# 不使用 docker ps 判断健康；每一项都做真实探活
# 依赖启动有先后（server 首次冷启动 + Flyway 可达 90s），关键探针带重试退避
# 用法：powershell -ExecutionPolicy Bypass -File scripts\health.ps1
# 兼容 Windows PowerShell 5.1 与 PowerShell 7（不依赖 7 独有参数）
$ErrorActionPreference = "Continue"

$root = Split-Path -Parent $PSScriptRoot
$failed = 0

# MySQL root 密码读取 .env（不存在则用 compose 默认值；仓库无真实密钥）
$envFile = Join-Path $root "deploy\docker\.env"
$mysqlRootPw = "devpassword"
if (Test-Path $envFile) {
    $m = Select-String -Path $envFile -Pattern '^\s*MYSQL_ROOT_PASSWORD=([^\s#]+)' | Select-Object -First 1
    if ($m) { $mysqlRootPw = $m.Matches[0].Groups[1].Value.Trim() }
}

function Check([string]$name, [scriptblock]$probe, [int]$retries = 1, [int]$sleepSec = 5) {
    $lastErr = $null
    for ($i = 1; $i -le $retries; $i++) {
        try {
            & $probe | Out-Null
            Write-Host ("[OK]   " + $name) -ForegroundColor Green
            return
        } catch {
            $lastErr = $_.Exception.Message
            if ($i -lt $retries) { Start-Sleep -Seconds $sleepSec }
        }
    }
    Write-Host ("[FAIL] " + $name + "  " + $lastErr) -ForegroundColor Red
    $script:failed++
}

# PS 5.1 下无 charset 的响应 Content 为 byte[]，统一转 UTF-8 文本再断言
function RespText($r) {
    if ($r.Content -is [byte[]]) { return [System.Text.Encoding]::UTF8.GetString($r.Content) }
    return [string]$r.Content
}

# 1) web HTTP（nginx 首页 200 且内容含根节点；80 = Stage 2.1 演示体验入口，5173 兼容旧脚本）
Check "web http (80 · 演示入口)" {
    $r = Invoke-WebRequest -Uri "http://localhost/" -UseBasicParsing -TimeoutSec 5
    if ($r.StatusCode -ne 200 -or -not (RespText $r).Contains('id="root"')) { throw "响应异常" }
} -retries 12

Check "web http (5173)" {
    $r = Invoke-WebRequest -Uri "http://localhost:5173/" -UseBasicParsing -TimeoutSec 5
    if ($r.StatusCode -ne 200 -or -not (RespText $r).Contains('id="root"')) { throw "响应异常" }
} -retries 12

# 2) server health（actuator UP；首次冷启动 + Flyway，重试覆盖 start_period 90s）
Check "server health (8080)" {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 5
    if (-not (RespText $r).Contains('"UP"')) { throw "health 非 UP" }
} -retries 36

# 3) MySQL（mysqladmin ping；密码经环境变量传入，不落入进程参数）
Check "mysql" {
    docker exec -e MYSQL_PWD="$mysqlRootPw" yunsie-mysql mysqladmin ping -h localhost -uroot --silent
} -retries 6

# 4) Redis（redis-cli ping → PONG）
Check "redis" {
    $out = docker exec yunsie-redis redis-cli ping
    if ($out -notmatch "PONG") { throw "非 PONG" }
} -retries 6

# 5) MinIO（health/live 200）
Check "minio" {
    $r = Invoke-WebRequest -Uri "http://localhost:9000/minio/health/live" -UseBasicParsing -TimeoutSec 5
    if ($r.StatusCode -ne 200) { throw "非 200" }
} -retries 6

# 6) /api/v1 反代链路（经 nginx 请求后端：业务失败仍返回统一 JSON，而非 index.html）
Check "api/v1 reverse proxy" {
    $body = '{"account":"__health_probe__","password":"x"}'
    $r = Invoke-WebRequest -Uri "http://localhost:5173/api/v1/user/auth/login" -Method Post `
        -Body $body -ContentType "application/json" -UseBasicParsing -TimeoutSec 5
    if ($r.Headers["Content-Type"] -notmatch "application/json") { throw "非 JSON 响应（可能回退到 index.html）" }
    if (-not (RespText $r).Contains('"code"')) { throw "无统一响应结构" }
} -retries 12

if ($failed -gt 0) {
    Write-Host ("`n健康检查失败项：{0}" -f $failed) -ForegroundColor Red
    exit 1
}
Write-Host "`n全部健康检查通过" -ForegroundColor Green
