[CmdletBinding()]
param([string]$JavaHome,[switch]$Packaged)
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
Select-ProjectJava -RequestedJavaHome $JavaHome
if(-not $env:BANGUMI_PROXY){
    try {
        $bangumiEndpoint=[uri]'https://api.bgm.tv'
        $bangumiProxy=[Net.Http.HttpClient]::DefaultProxy.GetProxy($bangumiEndpoint)
        if($bangumiProxy -and $bangumiProxy.Host -ne $bangumiEndpoint.Host -and $bangumiProxy.Scheme -eq 'http' -and -not $bangumiProxy.UserInfo){$env:BANGUMI_PROXY=$bangumiProxy.AbsoluteUri}
    } catch {}
}
if($env:BANGUMI_PROXY){
    $configuredProxy=[uri]$env:BANGUMI_PROXY
    Write-Output "Bangumi HTTP proxy: $($configuredProxy.Host):$($configuredProxy.Port)"
} else {Write-Output 'Bangumi HTTP proxy: none (direct connection).'}
Push-Location (Join-Path $root 'backend')
try {
    if($Packaged) {
        $jar=Join-Path $root 'backend/target/animegraph-backend-0.1.0-SNAPSHOT.jar'
        if(-not(Test-Path -LiteralPath $jar)){throw 'Run Build-Delivery.ps1 before starting packaged mode.'}
        & (Join-Path $env:JAVA_HOME 'bin/java.exe') -jar $jar
    } else { & .\mvnw.cmd spring-boot:run }
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
} finally { Pop-Location }
