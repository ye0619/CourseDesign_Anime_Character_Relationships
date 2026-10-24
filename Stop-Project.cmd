@echo off
setlocal
set "PROJECT_PWSH="
for /f "delims=" %%I in ('where pwsh.exe 2^>nul') do if not defined PROJECT_PWSH set "PROJECT_PWSH=%%I"
if not defined PROJECT_PWSH if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" set "PROJECT_PWSH=%ProgramFiles%\PowerShell\7\pwsh.exe"
if not defined PROJECT_PWSH if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" set "PROJECT_PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"
if not defined PROJECT_PWSH (
  echo PowerShell 7 was not found. Install PowerShell 7, then run this launcher again.
  exit /b 1
)
"%PROJECT_PWSH%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Stop-Project.ps1" %*
exit /b %ERRORLEVEL%
