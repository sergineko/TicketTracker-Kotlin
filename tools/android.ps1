param(
    [ValidateSet('validate','device','release')][string]$Action = 'validate'
)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$studioJavaPath = 'C:\Program Files\Android\Android Studio\jbr'
if (Test-Path -LiteralPath (Join-Path $studioJavaPath 'bin\java.exe')) { $env:JAVA_HOME = $studioJavaPath }
if (!$env:JAVA_HOME) { throw 'Configura JAVA_HOME con un JDK compatible con Gradle 9.3.1.' }
$env:GRADLE_USER_HOME = Join-Path $projectPath '.local\gradle-user-home'
$tasks = switch ($Action) {
    validate { @('spotlessCheck', ':domain:test', ':app:lintDebug', ':core:documents:lintDebug', ':core:database:lintDebug', ':app:assembleDebug') }
    device { @(':app:connectedDebugAndroidTest') }
    release { @(':app:assembleRelease') }
}
Push-Location $projectPath
try { & .\gradlew.bat @tasks --console=plain; $result = $LASTEXITCODE }
finally { Pop-Location }
exit $result
