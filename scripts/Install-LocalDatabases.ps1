[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$toolsDir = Join-Path $root '.local/tools'
$downloads = Join-Path $root '.local/downloads'
New-Item -ItemType Directory -Force -Path $toolsDir,$downloads | Out-Null
$packages = @(
    @{ Name='neo4j-community-5.26.16'; File='neo4j-community-5.26.16-windows.zip'; Url='https://dist.neo4j.org/neo4j-community-5.26.16-windows.zip'; Sha256='' },
    @{ Name='jdk-21.0.12.1+1-jre'; File='OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip'; Url='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip'; Sha256='d35f31e712f0fcf6ac5a093edc90204fbff22f720ba3950bd09d331d5e621636' }
)
$records = @()
foreach ($package in $packages) {
    $archive = Join-Path $downloads $package.File
    if (-not (Test-Path -LiteralPath $archive)) {
        Write-Output "Downloading $($package.File) into project .local"
        Invoke-WebRequest -Uri $package.Url -OutFile $archive -TimeoutSec 600
    }
    $hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($package.Sha256 -and $hash -ne $package.Sha256) { throw "Checksum mismatch: $($package.File). Do not execute this archive." }
    $destination = Join-Path $toolsDir $package.Name
    if (-not (Test-Path -LiteralPath $destination)) {
        Expand-Archive -LiteralPath $archive -DestinationPath $toolsDir
    }
    $records += @{name=$package.Name;url=$package.Url;sha256=$hash;upstreamChecksumVerified=[bool]$package.Sha256;fetchedAt=[DateTime]::UtcNow.ToString('o')}
    Write-Output "Ready: $($package.Name)"
}
$records | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $root '.local/database-downloads.json') -Encoding utf8
Write-Output 'Portable files prepared. No system service or global PATH was changed.'
