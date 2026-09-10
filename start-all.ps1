# schoolHelp 本地一键启动脚本（PowerShell v2 - 修复启动失败问题）
# 前置：MySQL(3306)、Nacos(8848)、表结构已导入
# 用法：powershell -ExecutionPolicy Bypass -File .\start-all.ps1
$ErrorActionPreference = "Stop"
$root = "D:\homework\schoolHelp\schoolHelp"
$logDir = "$root\logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# 显式定位 java（避免 PATH 问题）
$java = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot\bin\java.exe"
if (-not (Test-Path $java)) {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { $java = $cmd.Source } else { Write-Host "找不到 java，请设置 JAVA_HOME" -ForegroundColor Red; exit 1 }
}

$services = @(
    @{ name = "gateway"; jar = "$root\schoolhelp-gateway\target\schoolhelp-gateway.jar"; port = 8080 },
    @{ name = "user";    jar = "$root\schoolhelp-user\target\schoolhelp-user.jar";       port = 8101 },
    @{ name = "course";  jar = "$root\schoolhelp-course\target\schoolhelp-course.jar";   port = 8102 },
    @{ name = "biz";     jar = "$root\schoolhelp-biz\target\schoolhelp-biz.jar";         port = 8103 }
)

Write-Host "`n===== 启动 schoolHelp 4 服务 =====`n" -ForegroundColor Cyan

foreach ($svc in $services) {
    if (-not (Test-Path $svc.jar)) {
        Write-Host "[SKIP] $($svc.name) jar 不存在: $($svc.jar)" -ForegroundColor Yellow
        continue
    }
    $existing = Get-NetTCPConnection -LocalPort $svc.port -State Listen -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "[SKIP] $($svc.name) 端口 $($svc.port) 已被占用(PID $($existing.OwningProcess))" -ForegroundColor Yellow
        continue
    }
    $logFile = "$logDir\$($svc.name).log"
    $errFile = "$logDir\$($svc.name).err.log"
    $p = Start-Process -FilePath $java -ArgumentList @("-jar", $svc.jar) `
        -RedirectStandardOutput $logFile -RedirectStandardError $errFile -PassThru -WindowStyle Hidden
    Write-Host "[OK] $($svc.name) PID=$($p.Id) 端口=$($svc.port)" -ForegroundColor Green
}

Write-Host "`n===== 等待 30 秒让服务启动并注册 Nacos... =====" -ForegroundColor Cyan
Start-Sleep -Seconds 30

Write-Host "`n===== 端口检查 =====" -ForegroundColor Cyan
foreach ($svc in $services) {
    $conn = Get-NetTCPConnection -LocalPort $svc.port -State Listen -ErrorAction SilentlyContinue
    if ($conn) { Write-Host "[OK] $($svc.name) :$($svc.port) 已监听" -ForegroundColor Green }
    else {
        Write-Host "[FAIL] $($svc.name) :$($svc.port) 未监听" -ForegroundColor Red
        $err = "$logDir\$($svc.name).err.log"
        if (Test-Path $err) { Write-Host "--- $err 尾部 ---"; Get-Content $err -Tail 12 -Encoding UTF8 }
    }
}

Write-Host "`n===== 冒烟测试（注册 -> 登录）=====" -ForegroundColor Cyan
try {
    $regBody = @{ username = "teststu01"; password = "Test@123456"; confirmPassword = "Test@123456"; role = 0 } | ConvertTo-Json
    $reg = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/user/auth/register" -Method POST -Body $regBody -ContentType "application/json" -TimeoutSec 15
    Write-Host "[注册] " -NoNewline; $reg | ConvertTo-Json -Depth 5 -Compress

    $loginBody = @{ username = "teststu01"; password = "Test@123456" } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/user/auth/login" -Method POST -Body $loginBody -ContentType "application/json" -TimeoutSec 15
    Write-Host "[登录] " -NoNewline; $login | ConvertTo-Json -Depth 5 -Compress
} catch {
    Write-Host "冒烟测试失败: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n===== 完成 =====" -ForegroundColor Cyan
