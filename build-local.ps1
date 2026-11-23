# Build without Gradle / without downloading the IntelliJ SDK.
#
# Compiles the plugin against the JARs of an already installed IntelliJ-based IDE
# (GoLand, IDEA, ...) using the JBR (JetBrains Runtime) bundled with that IDE,
# then packs an installable plugin ZIP.
#
# Usage:
#   .\build-local.ps1
#   .\build-local.ps1 -Ide "C:\Program Files\JetBrains\GoLand 2024.1.4"
#
param(
    [string[]]$Ide = @(
        "C:\Program Files\JetBrains\GoLand 2024.1.4",
        "C:\Program Files\JetBrains\GoLand 2025.1",
        "C:\Program Files\JetBrains\GoLand 2026.1"
    )
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$idePath = $null
foreach ($candidate in $Ide) {
    if (Test-Path (Join-Path $candidate "jbr\bin\javac.exe")) {
        $idePath = $candidate
        break
    }
}

if ($null -eq $idePath) {
    throw "No IntelliJ-based IDE with JBR found. Pass -Ide 'C:\path\to\GoLand'."
}

Write-Host "Using IDE: $idePath"

$javac = Join-Path $idePath "jbr\bin\javac.exe"
$jars = Get-ChildItem (Join-Path $idePath "lib") -Filter *.jar | ForEach-Object { $_.FullName }
$classpath = $jars -join ";"

$outDir = Join-Path $root "build\local"
$classesDir = Join-Path $outDir "classes"
$jarPath = Join-Path $outDir "pulsing-highlights.jar"
$distDir = Join-Path $outDir "dist"
$pluginDir = Join-Path $distDir "pulsing-highlights"
$zipPath = Join-Path $outDir "pulsing-highlights-0.2.1.zip"

if (Test-Path $outDir) {
    Remove-Item $outDir -Recurse -Force
}
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

$sources = Get-ChildItem (Join-Path $root "src\main\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }

Write-Host "Compiling $($sources.Count) source file(s)..."
& $javac -encoding UTF-8 --release 17 -cp $classpath -d $classesDir $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

Copy-Item (Join-Path $root "src\main\resources\*") $classesDir -Recurse -Force

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

# NOTE: .NET Framework's ZipFile.CreateFromDirectory writes entry names with
# backslashes on Windows, which makes the JAR unreadable by the JVM. So build
# entries manually and always use forward slashes.
function New-ZipFromDirectory {
    param(
        [string]$SourceDir,
        [string]$ZipPath
    )

    if (Test-Path $ZipPath) {
        Remove-Item $ZipPath -Force
    }

    $source = (Resolve-Path $SourceDir).Path
    $zip = [System.IO.Compression.ZipFile]::Open($ZipPath, [System.IO.Compression.ZipArchiveMode]::Create)

    try {
        Get-ChildItem $source -Recurse -File | ForEach-Object {
            $relative = $_.FullName.Substring($source.Length).TrimStart('\', '/')
            $entryName = $relative -replace '\\', '/'
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $zip, $_.FullName, $entryName, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    }
    finally {
        $zip.Dispose()
    }
}

New-ZipFromDirectory -SourceDir $classesDir -ZipPath $jarPath

New-Item -ItemType Directory -Path (Join-Path $pluginDir "lib") -Force | Out-Null
Copy-Item $jarPath (Join-Path $pluginDir "lib")

# IMPORTANT: the ZIP must contain a top-level plugin directory
# (error-pulse/lib/error-pulse.jar). The IDE extracts the zip and treats its
# single root entry as the plugin directory, so zipping the *contents* of the
# plugin dir produces "Failed to load plugin descriptor".
New-ZipFromDirectory -SourceDir $distDir -ZipPath $zipPath

Write-Host ""
Write-Host "Done."
Write-Host "Plugin JAR: $jarPath"
Write-Host "Plugin ZIP: $zipPath"
Write-Host ""
Write-Host "Install: IDE -> Settings -> Plugins -> gear icon -> Install Plugin from Disk... -> $zipPath"







