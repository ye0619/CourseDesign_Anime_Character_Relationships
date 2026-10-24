$ErrorActionPreference = 'Stop'

function Resolve-ProjectPath {
    param([string]$Path)
    $absolute=[IO.Path]::GetFullPath($Path)
    $resolved=[IO.Path]::GetPathRoot($absolute)
    foreach($part in $absolute.Substring($resolved.Length).Split([IO.Path]::DirectorySeparatorChar,[StringSplitOptions]::RemoveEmptyEntries)) {
        $resolved=Join-Path $resolved $part
        $entry=Get-Item -LiteralPath $resolved -ErrorAction SilentlyContinue
        if($entry -and $entry.LinkType -in @('Junction','SymbolicLink')) {$resolved=$entry.ResolveLinkTarget($true).FullName}
    }
    return [IO.Path]::TrimEndingDirectorySeparator($resolved)
}

function Import-ProjectEnvironment {
    param([string]$Root)
    $file = Join-Path $Root '.env'
    if (-not (Test-Path -LiteralPath $file)) { return }
    foreach ($line in Get-Content -LiteralPath $file -Encoding utf8) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -gt 0) { [Environment]::SetEnvironmentVariable($name, $value, 'Process') }
        }
    }
    if (-not $env:MYSQL_URL) {
        $port = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3307' }
        $database = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { 'animegraph' }
        $env:MYSQL_URL = "jdbc:mysql://127.0.0.1:$port/${database}?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&connectTimeout=2000&socketTimeout=3000"
    }
}

function Select-ProjectJava {
    param([string]$RequestedJavaHome)
    $candidates = @($RequestedJavaHome, $env:JAVA_HOME)
    $javaDirectory = Join-Path $env:ProgramFiles 'Java'
    if (Test-Path -LiteralPath $javaDirectory) {
        $candidates += @(Get-ChildItem -LiteralPath $javaDirectory -Directory -Filter 'jdk*' | Sort-Object Name -Descending | ForEach-Object FullName)
    }
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $java = Join-Path $candidate 'bin/java.exe'
        $javac = Join-Path $candidate 'bin/javac.exe'
        if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $javac)) { continue }
        $version = (& $java --version 2>&1 | Out-String)
        if ($version -match '(?:java|openjdk)\s+(\d+)' -and [int]$Matches[1] -ge 21 -and [int]$Matches[1] -le 25) {
            $env:JAVA_HOME = $candidate
            return
        }
    }
    throw 'A JDK 21-25 is required. Set JAVA_HOME in the project .env or pass -JavaHome. The system default was not changed.'
}
