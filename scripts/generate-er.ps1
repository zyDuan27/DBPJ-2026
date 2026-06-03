[CmdletBinding()]
param(
    [string]$InputPath = "",
    [string]$OutputPath = "",
    [int]$Scale = 2
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")

if ([string]::IsNullOrWhiteSpace($InputPath)) {
    $docsDir = Join-Path $repoRoot "docs"
    $sourceFiles = @(Get-ChildItem -LiteralPath $docsDir -Filter "ER*.svg" | Select-Object -First 2)
    if ($sourceFiles.Count -eq 0) {
        throw "ER source file not found. Expected a docs/ER*.svg file."
    }
    if ($sourceFiles.Count -gt 1) {
        throw "Multiple ER source files found. Pass -InputPath to choose one."
    }
    $inputFile = $sourceFiles[0].FullName
} else {
    $inputFile = Join-Path $repoRoot $InputPath
}

if (-not (Test-Path -LiteralPath $inputFile)) {
    throw "ER source file not found: $inputFile"
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $outputFile = [System.IO.Path]::ChangeExtension($inputFile, ".png")
} else {
    $outputFile = Join-Path $repoRoot $OutputPath
}

$outputDir = Split-Path -Parent $outputFile
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

$npxArgs = @("--yes", "svgexport", $inputFile, $outputFile, "${Scale}x")

& npx @npxArgs

if ($LASTEXITCODE -ne 0) {
    throw "SVG export failed with exit code $LASTEXITCODE"
}

Write-Host "Generated ER diagram: $outputFile"
