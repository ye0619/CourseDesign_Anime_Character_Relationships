[CmdletBinding()]
param()
$ErrorActionPreference = 'Continue'
$tools = @('java', 'javac', 'node', 'npm', 'docker', 'mysql', 'git')
foreach ($tool in $tools) {
    $command = Get-Command $tool -ErrorAction SilentlyContinue
    [pscustomobject]@{ Tool = $tool; Path = if ($command) { $command.Source } else { 'NOT_FOUND' } }
}
Write-Output 'Default Java:'
java -version
Write-Output 'Node/npm:'
node --version
npm --version
Write-Output 'Listening project ports:'
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object LocalPort -in 3306, 3307, 7474, 7687, 8080, 5173 |
    Select-Object LocalAddress, LocalPort, OwningProcess
