[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$directory=Join-Path $root 'data/raw/bangumi/archive'
New-Item -ItemType Directory -Force -Path $directory|Out-Null
$headers=@{'User-Agent'='AnimeGraphGraduation/0.4 (official cached archive subset for graduation project)'}
$metadata=Invoke-RestMethod 'https://raw.githubusercontent.com/bangumi/Archive/master/aux/latest.json' -Headers $headers -TimeoutSec 30
if($metadata.browser_download_url -notmatch '^https://github\.com/bangumi/Archive/releases/download/archive/[^/]+\.zip$' -or $metadata.name -notmatch '^dump-[A-Za-z0-9.\-]+\.zip$'){throw 'Unexpected official archive location'}
$path=Join-Path $directory $metadata.name
if(-not(Test-Path -LiteralPath $path)){
    Write-Output "Downloading official snapshot $($metadata.name) ($([Math]::Round($metadata.size/1MB)) MiB), once into ignored data/raw."
    Invoke-WebRequest $metadata.browser_download_url -OutFile $path -Headers $headers -TimeoutSec 900
}
$expected=([string]$metadata.digest)-replace '^sha256:',''
$actual=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
if($expected -notmatch '^[0-9a-f]{64}$' -or $actual -ne $expected){throw 'Official snapshot checksum verification failed; archive will not be read.'}
$metadata|ConvertTo-Json -Depth 5|Set-Content (Join-Path $directory 'latest.json') -Encoding utf8
Write-Output 'Official snapshot SHA256 verified. Extracting only project character metadata and relevant relations.'
& python -c 'import runpy,sys; script=sys.argv.pop(1); runpy.run_path(script,run_name="__main__")' (Join-Path $PSScriptRoot 'Extract-BangumiArchive.py') $path
if($LASTEXITCODE -ne 0){throw 'Archive subset extraction failed.'}
