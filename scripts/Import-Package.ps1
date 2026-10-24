[CmdletBinding()]
param([string]$Path,[long]$TaskId=0,[string]$BaseUrl='http://127.0.0.1:8080',[int]$WaitSeconds=1800)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Project-Environment.ps1')
Import-ProjectEnvironment -Root $root
$csrf=(Invoke-RestMethod "$BaseUrl/api/v1/auth/csrf" -SessionVariable importSession).data
$login=Invoke-RestMethod "$BaseUrl/api/v1/auth/login" -Method Post -WebSession $importSession -Headers @{$csrf.headerName=$csrf.token} -Body @{username=$env:ADMIN_USERNAME;password=$env:ADMIN_PASSWORD}
if(-not $login.data.roles.Contains('ADMIN')){throw 'Administrator role required.'}
$csrf=(Invoke-RestMethod "$BaseUrl/api/v1/auth/csrf" -WebSession $importSession).data
if($TaskId -gt 0){$task=(Invoke-RestMethod "$BaseUrl/api/v1/admin/imports/$TaskId" -WebSession $importSession).data}
else{
    if(-not $Path){throw 'Specify -Path to upload, or -TaskId to observe an existing task.'}
    $task=(Invoke-RestMethod "$BaseUrl/api/v1/admin/imports" -Method Post -WebSession $importSession -Headers @{$csrf.headerName=$csrf.token} -Form @{file=Get-Item -LiteralPath $Path}).data
    Write-Output "Created task $($task.id) ($($task.totalCount) records)"
}
$deadline=[DateTime]::UtcNow.AddSeconds($WaitSeconds)
$lastCheckpoint=-1
do {
    Start-Sleep -Seconds 2
    $task=(Invoke-RestMethod "$BaseUrl/api/v1/admin/imports/$($task.id)" -WebSession $importSession).data
    if($task.checkpoint -ne $lastCheckpoint){Write-Output "Task $($task.id): $($task.status) $($task.checkpoint)/$($task.totalCount), failed $($task.failedCount)";$lastCheckpoint=$task.checkpoint}
}while($task.status -in @('PENDING','RUNNING') -and [DateTime]::UtcNow -lt $deadline)
if($task.status -ne 'SUCCEEDED'){
    $records=(Invoke-RestMethod "$BaseUrl/api/v1/admin/imports/$($task.id)/records?status=FAILED" -WebSession $importSession).data
    $records.items|Select-Object recordKey,errorCode,errorMessage|ConvertTo-Json
    throw "Task $($task.id) ended or is still waiting: $($task.status)"
}
Write-Output "Task $($task.id) succeeded: $($task.successCount) records."
