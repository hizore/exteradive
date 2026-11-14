$ErrorActionPreference = "Continue"
Set-Location F:\exteraless

# exteradive: обновление базы до exteraless 13.2.2 (метод рефока 13.1.1).
# BASE = снапшот upstream, от которого делали рефок 13.1.1 (11-05)
# OURS = наш форк (HEAD на момент запуска)
# THEIRS = upstream exteraless 13.2.2 (main, включая пост-релизные фиксы)
$BASE   = "b8eaea9087"
$OURS   = "dddc5198b2"
$THEIRS = "31a35e4683"
$gitPath = (Get-Command git.exe).Source

$SCOPE = @(
  "TMessagesProj/src/main/java",
  "TMessagesProj/src/main/kotlin",
  "TMessagesProj/src/main/res",
  "TMessagesProj/src/main/assets",
  "TMessagesProj/src/main/AndroidManifest.xml",
  "TMessagesProj/build.gradle",
  "TMessagesProj/proguard-rules.pro",
  "build.gradle",
  "gradle.properties"
)

# политика: не тянем обратно то, что форк удалил сознательно (плагины, OTA, python, chaquopy)
function PolicySkip($p) {
  if ($p -like "TMessagesProj/src/main/java/app/exteraless/plugins/*") { return $true }
  if ($p -like "TMessagesProj/src/main/java/app/exteraless/updater/*") { return $true }
  if ($p -like "TMessagesProj/src/main/java/com/exteragram/*") { return $true }
  if ($p -like "*strings_oe_plugins*") { return $true }
  if ($p -like "*msg_plugins*") { return $true }
  if ($p -like "*plugins_filled*") { return $true }
  if ($p -like "TMessagesProj/src/main/python/*") { return $true }
  if ($p -like "TMessagesProj/chaquopy*") { return $true }
  if ($p -like ".github*") { return $true }
  if ($p -like "*google-services.json*") { return $true }
  if ($p -like "*.gitignore*" -or $p -like "*.gitmodules*") { return $true }
  return $false
}

# карта path -> @{ rev = blobsha; kind = blob|commit }
function Get-TreeMap($rev, $map) {
  foreach ($scope in $SCOPE) {
    $lines = @(git ls-tree -r $rev -- $scope)
    foreach ($line in $lines) {
      if (-not $line) { continue }
      $tab = $line.IndexOf("`t")
      if ($tab -lt 0) { continue }
      $meta = $line.Substring(0, $tab)
      $path = $line.Substring($tab + 1)
      $parts = $meta -split " ", 3
      $kind = if ($parts[1] -eq "commit") { "commit" } else { "blob" }
      if ($map.ContainsKey($path)) { $map[$path][$rev] = $parts[2]; $map[$path]["kind"] = $kind }
      else {
        $entry = @{ $rev = $parts[2]; "kind" = $kind }
        $map[$path] = $entry
      }
    }
  }
}

$map = @{}
Get-TreeMap $BASE $map
Get-TreeMap $OURS $map
Get-TreeMap $THEIRS $map

Write-Host "PATHS: $($map.Count)"

$report = New-Object System.Collections.Generic.List[string]
$conflicts = New-Object System.Collections.Generic.List[string]
$work = 0
$tmpb = Join-Path $env:TEMP "d3_base.bin"
$tmpo = Join-Path $env:TEMP "d3_ours.bin"
$tmpt = Join-Path $env:TEMP "d3_theirs.bin"

$shown = 0
foreach ($p in ($map.Keys | Sort-Object)) {
  if (PolicySkip $p) { $report.Add("POLICY-SKIP`t$p"); continue }
  $e = $map[$p]
  $kind = $e["kind"]
  $bh = if ($e.Contains($BASE))   { $e[$BASE] }   else { $null }
  $oh = if ($e.Contains($OURS))   { $e[$OURS] }   else { $null }
  $th = if ($e.Contains($THEIRS)) { $e[$THEIRS] } else { $null }

  # подмодули (gitlink): наш указатель не менялся -> берём upstream
  if ($kind -eq "commit") {
    if ($oh -ne $th) {
      if ($bh -eq $oh -or $null -eq $bh) {
        git checkout -q $THEIRS -- $p 2>$null
        $report.Add("GITLINK-THEIRS`t$p"); $work++
      } else {
        $conflicts.Add($p)
        $report.Add("GITLINK-CONFLICT`t$p")
      }
    }
    continue
  }

  if ($null -eq $oh) {
    if ($null -ne $th) {
      if ($null -ne $bh) {
        # файл был в base, удалён форком; upstream мог его менять
        if ($bh -ne $th) { $report.Add("FORK-DELETED-UPD-MOD`t$p") }
        else { $report.Add("FORK-DELETED`t$p") }
        continue
      }
      if ($null -eq $bh) {
        # новый файл upstream
        git checkout -q $THEIRS -- $p
        $report.Add("ADDED-UPSTREAM`t$p"); $work++
      }
      continue
    }
    continue
  }

  if ($null -eq $th) {
    if ($null -ne $bh -and $oh -eq $bh) {
      # upstream удалил файл, мы не меняли -> удаляем
      git rm -q -f --ignore-unmatch -- $p 2>$null | Out-Null
      if (Test-Path $p) { Remove-Item -Force $p }
      $report.Add("DELETED-UPSTREAM`t$p"); $work++
    } else {
      $report.Add("OURS-ONLY-KEEP`t$p")
    }
    continue
  }

  if ($oh -eq $th) { continue }          # совпадает с upstream — не трогаем
  if ($oh -eq $bh) {                      # форк не менял -> берём upstream целиком
    git checkout -q $THEIRS -- $p
    $report.Add("THEIRS`t$p"); $work++
    continue
  }
  if ($bh -eq $th) {                      # upstream не менял -> форк остаётся
    $report.Add("OURS`t$p")
    continue
  }

  # меняли оба: бинарник -> theirs, текст -> 3-way merge-file
  $bytes = [IO.File]::ReadAllBytes((Resolve-Path $p))
  $isBinary = $false
  for ($i = 0; $i -lt [Math]::Min($bytes.Length, 8000); $i++) { if ($bytes[$i] -eq 0) { $isBinary = $true; break } }
  if ($isBinary) {
    git checkout -q $THEIRS -- $p
    $conflicts.Add($p)
    $report.Add("BINARY-CONFLICT`t$p")
    continue
  }

  cmd /c "`"$gitPath`" show `"$($BASE):$p`" > `"$tmpb`"" 2>$null
  cmd /c "`"$gitPath`" show `"$($OURS):$p`" > `"$tmpo`"" 2>$null
  cmd /c "`"$gitPath`" show `"$($THEIRS):$p`" > `"$tmpt`"" 2>$null
  if (-not ((Test-Path $tmpb) -and (Test-Path $tmpo) -and (Test-Path $tmpt))) {
    $conflicts.Add($p); $report.Add("SHOW-FAIL`t$p")
    git checkout -q $THEIRS -- $p 2>$null
    continue
  }
  git merge-file -q -L ours -L base -L theirs $tmpo $tmpb $tmpt 2>$null | Out-Null
  $rc = $LASTEXITCODE
  if ($rc -eq 0) {
    Copy-Item -Force $tmpo $p
    $report.Add("MERGE-OK`t$p"); $work++
  } else {
    Copy-Item -Force $tmpo $p     # оставляем ours с маркерами конфликта для ручной разборки
    $conflicts.Add($p)
    $report.Add("CONFLICT`t$p (rc=$rc)")
  }
  Remove-Item -Force $tmpb, $tmpo, $tmpt -ErrorAction SilentlyContinue
  $shown++
  if (($shown % 100) -eq 0) { Write-Host "processed-both: $shown" }
}

$report | Set-Content -Encoding utf8 F:\dive_merge_report.txt
$conflicts | Set-Content -Encoding utf8 F:\dive_merge_conflicts.txt
$report | Group-Object { ($_ -split "`t")[0] } | ForEach-Object { Write-Host "$($_.Name) = $($_.Count)" }
Write-Host "Work changes=$work Conflicts=$($conflicts.Count)"
