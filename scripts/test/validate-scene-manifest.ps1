[CmdletBinding()]
param(
    [string]$ManifestPath = (Join-Path $PSScriptRoot '..\..\test-data\scene-manifest.example.tsv'),
    [string]$DataRoot = (Join-Path $PSScriptRoot '..\..\test-data'),
    [switch]$RequireLabeledMedia
)

$ErrorActionPreference = 'Stop'

$expectedHeaders = @(
    'scene_id',
    'category',
    'ground_truth',
    'consent',
    'media_path',
    'owner_visible',
    'notes'
)
$allowedGroundTruth = @(
    'pending',
    'open_walkable',
    'obstacle_partial',
    'obstacle_full',
    'drop_or_step',
    'person_present',
    'branching',
    'unscorable'
)
$allowedConsent = @('required', 'granted', 'not_applicable', 'denied')
$allowedOwnerVisible = @('yes', 'no', 'unknown')
$imageExtensions = @('.png', '.jpg', '.jpeg', '.webp')
$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

function Add-Error([string]$Message) {
    $script:errors.Add($Message)
}

function Test-UnderRoot([string]$Candidate, [string]$Root) {
    $separator = [System.IO.Path]::DirectorySeparatorChar
    $rootPrefix = $Root.TrimEnd([char[]]@('\', '/')) + $separator
    return $Candidate.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)
}

$manifestResolved = Resolve-Path -LiteralPath $ManifestPath -ErrorAction SilentlyContinue
$rootResolved = Resolve-Path -LiteralPath $DataRoot -ErrorAction SilentlyContinue
if ($null -eq $manifestResolved) {
    Write-Error "Manifest file does not exist: $ManifestPath"
    exit 2
}
if ($null -eq $rootResolved -or -not (Test-Path -LiteralPath $rootResolved.Path -PathType Container)) {
    Write-Error "Data root directory does not exist: $DataRoot"
    exit 2
}

$manifestFullPath = [System.IO.Path]::GetFullPath($manifestResolved.Path)
$dataRootFullPath = [System.IO.Path]::GetFullPath($rootResolved.Path).TrimEnd([char[]]@('\', '/'))
$rawLines = @(Get-Content -LiteralPath $manifestFullPath -Encoding UTF8)
$contentLines = @($rawLines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and -not $_.TrimStart().StartsWith('#') })
if ($contentLines.Count -eq 0) {
    Write-Error 'Manifest has no TSV header.'
    exit 2
}

$actualHeaders = @($contentLines[0].Split([char[]]@([char]9), [System.StringSplitOptions]::None))
if ($actualHeaders.Count -ne $expectedHeaders.Count -or
    (($actualHeaders -join "`t") -cne ($expectedHeaders -join "`t"))) {
    Add-Error "Header must match exactly: $($expectedHeaders -join ', ')"
}

$records = @()
if ($contentLines.Count -gt 1) {
    $records = @($contentLines[1..($contentLines.Count - 1)] | ConvertFrom-Csv -Delimiter "`t" -Header $actualHeaders)
}

$seenIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$categoryCounts = @{}
$categoryMediaCounts = @{}
$categoryLabeledMediaCounts = @{}
$truthCounts = @{}
$mediaCount = 0
$pendingMediaCount = 0
$scorableMediaCount = 0
$unscorableMediaCount = 0

for ($index = 0; $index -lt $records.Count; $index++) {
    $record = $records[$index]
    $rowNumber = $index + 2
    $id = [string]$record.scene_id
    $category = [string]$record.category
    $truth = ([string]$record.ground_truth).Trim().ToLowerInvariant()
    $consent = ([string]$record.consent).Trim().ToLowerInvariant()
    $mediaPath = ([string]$record.media_path).Trim()
    $ownerVisible = ([string]$record.owner_visible).Trim().ToLowerInvariant()

    if ([string]::IsNullOrWhiteSpace($id) -or $id -notmatch '^[a-z0-9][a-z0-9-]{0,63}$') {
        Add-Error "Row $rowNumber has an invalid scene_id. Use 1-64 lowercase letters, digits, and hyphens."
    } elseif (-not $seenIds.Add($id)) {
        Add-Error "Row $rowNumber duplicates scene_id '$id'."
    }

    if ([string]::IsNullOrWhiteSpace($category) -or $category -notmatch '^[a-z][a-z0-9_-]{1,63}$') {
        Add-Error "Row $rowNumber has an invalid category slug."
    } else {
        if (-not $categoryCounts.ContainsKey($category)) { $categoryCounts[$category] = 0 }
        $categoryCounts[$category]++
        if (-not $categoryMediaCounts.ContainsKey($category)) { $categoryMediaCounts[$category] = 0 }
        if (-not $categoryLabeledMediaCounts.ContainsKey($category)) { $categoryLabeledMediaCounts[$category] = 0 }
    }

    if ($truth -notin $allowedGroundTruth) {
        Add-Error "Row $rowNumber has unsupported ground_truth '$truth'."
    } else {
        if (-not $truthCounts.ContainsKey($truth)) { $truthCounts[$truth] = 0 }
        $truthCounts[$truth]++
    }
    if ($consent -notin $allowedConsent) {
        Add-Error "Row $rowNumber has unsupported consent state '$consent'."
    }
    if ($ownerVisible -notin $allowedOwnerVisible) {
        Add-Error "Row $rowNumber has invalid owner_visible value '$ownerVisible'."
    }

    if ([string]::IsNullOrWhiteSpace($mediaPath)) {
        if ($truth -ne 'pending' -and $truth -ne 'unscorable') {
            Add-Error "Row $rowNumber has a labeled ground_truth but no media_path."
        }
        continue
    }

    $mediaCount++
    if ($categoryMediaCounts.ContainsKey($category)) { $categoryMediaCounts[$category]++ }
    if ($truth -eq 'pending') { $pendingMediaCount++ }
    if ($truth -eq 'unscorable') {
        $unscorableMediaCount++
    } else {
        $scorableMediaCount++
        if ($categoryLabeledMediaCounts.ContainsKey($category)) {
            $categoryLabeledMediaCounts[$category]++
        }
    }
    if ($consent -ne 'granted') {
        Add-Error "Row $rowNumber has media but consent is '$consent'; every stored image needs recorded consent."
    }
    if ($ownerVisible -eq 'yes' -and $consent -ne 'granted') {
        Add-Error "Row $rowNumber marks a person visible but does not record granted consent."
    }
    if ($truth -eq 'pending') {
        Add-Error "Row $rowNumber has media that is still pending human ground-truth labeling."
    }
    if ($ownerVisible -eq 'unknown') {
        Add-Error "Row $rowNumber has media but owner_visible is unknown; review the image before evaluation."
    }

    if ([System.IO.Path]::IsPathRooted($mediaPath) -or $mediaPath -match '(^|[\\/])\.\.([\\/]|$)') {
        Add-Error "Row $rowNumber media_path must be a relative path inside the data root."
        continue
    }
    $extension = [System.IO.Path]::GetExtension($mediaPath).ToLowerInvariant()
    if ($extension -notin $imageExtensions) {
        Add-Error "Row $rowNumber media must be a still image ($($imageExtensions -join ', ')); videos are not accepted."
        continue
    }

    $candidatePath = [System.IO.Path]::GetFullPath((Join-Path $dataRootFullPath $mediaPath))
    if (-not (Test-UnderRoot -Candidate $candidatePath -Root $dataRootFullPath)) {
        Add-Error "Row $rowNumber media_path resolves outside the data root."
        continue
    }
    $mediaResolved = Resolve-Path -LiteralPath $candidatePath -ErrorAction SilentlyContinue
    if ($null -eq $mediaResolved -or -not (Test-Path -LiteralPath $mediaResolved.Path -PathType Leaf)) {
        Add-Error "Row $rowNumber media file does not exist under the data root."
        continue
    }
    $resolvedMediaPath = [System.IO.Path]::GetFullPath($mediaResolved.Path)
    if (-not (Test-UnderRoot -Candidate $resolvedMediaPath -Root $dataRootFullPath)) {
        Add-Error "Row $rowNumber media file resolves outside the data root (including linked paths)."
    }
}

if ($mediaCount -eq 0) {
    $warnings.Add('No media files are listed. The manifest is schema-valid only; evaluation is not ready.')
}
if ($RequireLabeledMedia -and ($scorableMediaCount -eq 0 -or $pendingMediaCount -gt 0)) {
    Add-Error 'Evaluation requires at least one scorable media file and no media may remain pending human review.'
}

Write-Output "MANIFEST_FILE=$([System.IO.Path]::GetFileName($manifestFullPath))"
Write-Output "ROWS=$($records.Count) MEDIA=$mediaCount SCORABLE_MEDIA=$scorableMediaCount PENDING_MEDIA=$pendingMediaCount UNSCORABLE_MEDIA=$unscorableMediaCount"
$evaluationReady = $errors.Count -eq 0 -and $scorableMediaCount -gt 0 -and $pendingMediaCount -eq 0
Write-Output "SCHEMA_VALID=$($errors.Count -eq 0) EVALUATION_READY=$evaluationReady"
Write-Output 'CATEGORY_COVERAGE:'
foreach ($key in @($categoryCounts.Keys | Sort-Object)) {
    Write-Output "  $key rows=$($categoryCounts[$key]) media=$($categoryMediaCounts[$key]) labeled_media=$($categoryLabeledMediaCounts[$key])"
}
Write-Output 'GROUND_TRUTH_COVERAGE:'
foreach ($key in @($truthCounts.Keys | Sort-Object)) {
    Write-Output "  $key=$($truthCounts[$key])"
}
foreach ($warning in $warnings) { Write-Warning $warning }
foreach ($errorMessage in $errors) { Write-Error $errorMessage -ErrorAction Continue }
if ($errors.Count -gt 0) { exit 1 }
exit 0
