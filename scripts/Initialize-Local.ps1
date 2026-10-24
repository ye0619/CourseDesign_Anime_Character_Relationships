[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$target = Join-Path $root '.env'
if (Test-Path -LiteralPath $target) { throw '.env already exists; edit it explicitly instead of overwriting secrets.' }
function New-LocalSecret {
    $bytes = New-Object byte[] 24
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}
$content = Get-Content -LiteralPath (Join-Path $root '.env.example') -Raw -Encoding utf8
$content = $content.Replace('MYSQL_PASSWORD=', ('MYSQL_PASSWORD=' + (New-LocalSecret)))
$content = $content.Replace('MYSQL_ROOT_PASSWORD=', ('MYSQL_ROOT_PASSWORD=' + (New-LocalSecret)))
$content = $content.Replace('NEO4J_PASSWORD=', ('NEO4J_PASSWORD=' + (New-LocalSecret)))
$adminPassword=New-LocalSecret
$content = $content.Replace('ADMIN_PASSWORD=', ('ADMIN_PASSWORD=' + $adminPassword))
[IO.File]::WriteAllText($target, $content, (New-Object Text.UTF8Encoding $false))
$privateDir=Join-Path $root '.local'
New-Item -ItemType Directory -Path $privateDir -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $privateDir 'admin-account.txt'),"用户名：admin`n密码：$adminPassword`n",[Text.UTF8Encoding]::new($false))
Write-Output 'Created project-local .env with random database secrets. No system settings were changed.'
