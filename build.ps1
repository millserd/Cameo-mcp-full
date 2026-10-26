# Builds the Cameo MCP plugin. Asks for the Cameo install dir unless CAMEO_HOME is set.
# Usage: .\build.ps1 [gradle tasks...]   (default: rdzip dist)
$tasks = if ($args.Count) { $args } else { @('rdzip', 'dist') }
$cameo = $env:CAMEO_HOME
while (-not $cameo -or -not (Test-Path (Join-Path $cameo 'lib'))) {
    if ($cameo) { Write-Host "No 'lib' folder in '$cameo'." }
    $cameo = (Read-Host 'Cameo / CATIA Magic install directory').Trim('"', ' ')
}
gradle @tasks "-PcameoHome=$cameo"
exit $LASTEXITCODE
