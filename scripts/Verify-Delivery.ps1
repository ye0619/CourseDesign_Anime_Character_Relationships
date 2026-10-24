[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Path)
$ErrorActionPreference='Stop'
$target=(Resolve-Path -LiteralPath $Path).Path
if(Test-Path -LiteralPath $target -PathType Container){
    & node (Join-Path $PSScriptRoot 'Prepare-Delivery.mjs') --verify $target
    if($LASTEXITCODE -ne 0){throw 'Delivery folder validation failed.'}
    return
}
Add-Type -AssemblyName System.IO.Compression
$archive=[IO.Compression.ZipFile]::OpenRead($target)
try{
    $entry=$archive.GetEntry('delivery-manifest.json')
    if(-not $entry -or $entry.Length -gt 8MB){throw 'Archive manifest missing or too large.'}
    $reader=[IO.StreamReader]::new($entry.Open())
    try{$manifest=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
    if($manifest.schemaVersion -ne 2 -or $manifest.files.Count -lt 1 -or $manifest.files.Count -gt 20000){throw 'Invalid archive manifest.'}
    $known=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    [void]$known.Add('delivery-manifest.json')
    foreach($file in $manifest.files){
        $name=[string]$file.path
        if($name -match '(^/|[\\:]|(^|/)\.\.?(/|$)|(^|/)(\.local|\.git|\.agents|\.codex|node_modules|raw|normalized)(/|$)|(^|/)\.env(?!\.example$)|\.(pem|key|p12|pfx|jks)$)' -or -not $known.Add($name) -or $file.sha256 -notmatch '^[a-f0-9]{64}$'){throw 'Unsafe or duplicate archive path.'}
        $content=$archive.GetEntry($name)
        if(-not $content -or $content.Length -ne [long]$file.bytes){throw "Archive size mismatch: $name"}
        $stream=$content.Open();$hash=[Security.Cryptography.SHA256]::Create()
        try{$actual=[Convert]::ToHexString($hash.ComputeHash($stream)).ToLowerInvariant()}finally{$stream.Dispose();$hash.Dispose()}
        if($actual -ne $file.sha256){throw "Archive integrity mismatch: $name"}
    }
    if($archive.Entries.Count -ne $known.Count){throw 'Unlisted or duplicate entries in archive.'}
    foreach($item in $archive.Entries){if(-not $known.Contains($item.FullName)){throw 'Unlisted archive path.'}}
    Write-Output ('Archive verified: '+$manifest.files.Count+' files; contract '+$manifest.apiContractVersion+'.')
}finally{$archive.Dispose()}
