[CmdletBinding()]
param([string]$JavaHome,[string]$MySqlHome='D:\Tools\mysql-9.3.0-winx64',[switch]$SkipDatabases,[ValidateSet('Development','Release')][string]$Mode='Development')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
if(-not(Test-Path -LiteralPath (Join-Path $root '.env'))){ & (Join-Path $PSScriptRoot 'Initialize-Local.ps1') }
Import-ProjectEnvironment -Root $root
Select-ProjectJava -RequestedJavaHome $JavaHome
$backendJava=$env:JAVA_HOME
if(-not $SkipDatabases){
    & (Join-Path $PSScriptRoot 'Install-LocalDatabases.ps1')
    & (Join-Path $PSScriptRoot 'Start-LocalDatabases.ps1') -MySqlHome $MySqlHome
}
$env:JAVA_HOME=$backendJava
$local=Join-Path $root '.local'
New-Item -ItemType Directory -Path $local -Force|Out-Null
$shellPath=(Get-Process -Id $PID).Path
function Test-Port([int]$Port){$socket=[Net.Sockets.TcpClient]::new();try{$socket.Connect('127.0.0.1',$Port);return $true}catch{return $false}finally{$socket.Dispose()}}
function Start-ProjectComponent([string]$Name,[string]$Script,[int]$Port,[string[]]$ExtraArguments=@()){
    if(Test-Port $Port){
        try {
            if($Name -eq 'backend') {
                $probe=Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/health/live" -TimeoutSec 5
                if($probe.code -ne 'OK' -or $probe.data.status -ne 'UP'){throw 'Unexpected backend response'}
            } else {
                $probe=Invoke-WebRequest "http://127.0.0.1:$Port/" -TimeoutSec 5
                if($probe.Content -notmatch '<title>映间'){throw 'Unexpected frontend response'}
            }
        } catch {throw "Port $Port is occupied but cannot be identified as $Name. Inspect the process before restarting."}
        Write-Output "$Name already listens on port $Port; reusing it.";return
    }
    $argsList=@('-NoProfile','-ExecutionPolicy','Bypass','-File',('"{0}"' -f (Join-Path $PSScriptRoot $Script)))+$ExtraArguments
    $process=Start-Process -FilePath $shellPath -ArgumentList $argsList -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local "$Name-console.log") -RedirectStandardError (Join-Path $local "$Name-error.log")
    $process.Id|Set-Content -LiteralPath (Join-Path $local "$Name-launcher.pid")
}
$backendArguments=@();$frontendArguments=@()
if($Mode -eq 'Release'){$backendArguments=@('-Packaged');$frontendArguments=@('-Preview')}
Start-ProjectComponent 'backend' 'Start-Backend.ps1' ([int]$env:SERVER_PORT) $backendArguments
Start-ProjectComponent 'frontend' 'Start-Frontend.ps1' 5173 $frontendArguments
$ready=$false
for($i=0;$i -lt 60;$i++){
    try{$response=Invoke-RestMethod "http://127.0.0.1:$($env:SERVER_PORT)/api/v1/health/ready" -TimeoutSec 3;if($response.data.status -eq 'READY' -and (Test-Port 5173)){$ready=$true;break}}catch{}
    Start-Sleep -Seconds 1
}
if(-not $ready){throw 'Services are not ready. Inspect .local/*-console.log and *-error.log.'}
Write-Output 'Project ready: http://127.0.0.1:5173/anime'
if($env:LEGACY_ADMIN_ENABLED -eq 'true'){Write-Output 'Legacy maintenance API enabled. Private credentials: .local/admin-account.txt'}
else {Write-Output 'Local login is disabled. Associate a Bangumi account on the library page.'}
