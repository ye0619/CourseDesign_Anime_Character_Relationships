[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
& node (Join-Path $PSScriptRoot 'Prepare-Delivery.mjs')
if($LASTEXITCODE -ne 0){throw 'Delivery preparation failed.'}
$manifestPath=Join-Path $root '.local/delivery-manifest.json'
$manifest=Get-Content -LiteralPath $manifestPath -Raw|ConvertFrom-Json
$folder=[IO.Path]::GetFullPath($manifest.packageDirectory)
$allowed=[IO.Path]::GetFullPath((Join-Path $root '.local/deliveries'))+[IO.Path]::DirectorySeparatorChar
if(-not $folder.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected delivery output path.'}
$zip=$folder+'.zip'
if(Test-Path -LiteralPath $zip){throw 'Delivery archive already exists; refusing overwrite.'}
Add-Type -AssemblyName System.IO.Compression
[IO.Compression.ZipFile]::CreateFromDirectory($folder,$zip,[IO.Compression.CompressionLevel]::Optimal,$false)
& (Join-Path $PSScriptRoot 'Verify-Delivery.ps1') -Path $zip
$manifest|Add-Member -NotePropertyName archiveFile -NotePropertyValue $zip
$manifest|Add-Member -NotePropertyName archiveSha256 -NotePropertyValue (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $manifestPath -Encoding utf8
[IO.File]::WriteAllText(($zip+'.sha256'),($manifest.archiveSha256+'  '+[IO.Path]::GetFileName($zip)+[Environment]::NewLine),[Text.UTF8Encoding]::new($false))
Write-Output ('Delivery archive: '+$zip)
Write-Output 'Built, hashed and verified. External runtimes, npm dependencies, database data and credentials are not bundled.'
