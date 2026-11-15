$ErrorActionPreference = "Continue"
Set-Location F:\exteraless

# exteradive: доводка натива jni/ до upstream 13.2.2 (jni не входил в SCOPE dive_merge_1322).
# BASE = upstream 13.1.1, OURS = форк 13.1.1, THEIRS = upstream 13.2.2
$BASE   = "b8eaea9087"
$OURS   = "dddc5198b2"
$THEIRS = "31a35e4683"
$gitPath = (Get-Command git.exe).Source

$SCOPE = @("TMessagesProj/jni")

# Форк-специфика: оставляем OURS безусловно
function PolicyOurs($p) {
  if ($p -like "TMessagesProj/jni/CMakeLists.txt") { return $true }              # форк-сборка (prebuilt prisma, без libtwidget)
  if ($p -like "TMessagesProj/jni/Application.mk") { return $true }
  if ($p -like "TMessagesProj/jni/prebuild/*") { return $true }                  # форк-бинарники
  if ($p -like "TMessagesProj/jni/third_party/*") { return $true }               # сабмодули форка
  if ($p -like "TMessagesProj/jni/prisma/*") { return $true }                    # прisma-мост под старый сабмодуль
  if ($p -like "TMessagesProj/jni/twidget/*") { return $true }                   # форк: без libtwidget
  if ($p -like "TMessagesProj/jni/openssl/*") { return $true }                   # форк: aes_ige
  if ($p -like "TMessagesProj/jni/ffmpeg_jni.cc") { return $true }               # форк: копия в корне
  if ($p -like "TMessagesProj/jni/audio.c") { return $true }                     # форк: сильно изменён (917 строк)
  if ($p -like "TMessagesProj/jni/tests.cpp") { return $true }
  if ($p -like "TMessagesProj/jni/td") { return $true }
  if ($p -like "TMessagesProj/jni/tgcalls") { return $true }
  if ($p -like "TMessagesProj/jni/tgnet/TLSHello.*") { return $true }            # форк: бэкпорт
  return $false
}

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
      else { $map[$path] = @{ $rev = $parts[2]; "kind" = $kind } }
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
$tmpb = Join-Path $env:TEMP "d3j_base.bin"
$tmpo = Join-Path $env:TEMP "d3j_ours.bin"
$tmpt = Join-Path $env:TEMP "d3j_theirs.bin"

foreach ($p in ($map.Keys | Sort-Object)) {
  if (PolicyOurs $p) { $report.Add("OURS-KEEP`t$p"); continue }
  $e = $map[$p]
  $kind = $e["kind"]
  $bh = if ($e.Contains($BASE))   { $e[$BASE] }   else { $null }
  $oh = if ($e.Contains($OURS))   { $e[$OURS] }   else { $null }
  $th = if ($e.Contains($THEIRS)) { $e[$THEIRS] } else { $null }

  if ($kind -eq "commit") {
    if ($oh -ne $th) {
      if ($bh -eq $oh -or $null -eq $bh) {
        git checkout -q $THEIRS -- $p 2>$null
        $report.Add("GITLINK-THEIRS`t$p"); $work++
      } else {
        $conflicts.Add($p); $report.Add("GITLINK-CONFLICT`t$p")
      }
    }
    continue
  }

  if ($null -eq $oh) {
    if ($null -ne $th) {
      if ($null -ne $bh) {
        if ($bh -ne $th) { $report.Add("FORK-DELETED-UPD-MOD`t$p") } else { $report.Add("FORK-DELETED`t$p") }
        continue
      }
      git checkout -q $THEIRS -- $p
      $report.Add("ADDED-UPSTREAM`t$p"); $work++
      continue
    }
    continue
  }

  if ($null -eq $th) {
    if ($null -ne $bh -and $oh -eq $bh) {
      git rm -q -f --ignore-unmatch -- $p 2>$null | Out-Null
      if (Test-Path $p) { Remove-Item -Force $p }
      $report.Add("DELETED-UPSTREAM`t$p"); $work++
    } else {
      $report.Add("OURS-ONLY-KEEP`t$p")
    }
    continue
  }

  if ($oh -eq $th) { continue }
  if ($oh -eq $bh) {
    git checkout -q $THEIRS -- $p
    $report.Add("THEIRS`t$p"); $work++
    continue
  }
  if ($bh -eq $th) {
    $report.Add("OURS`t$p")
    continue
  }

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
    Copy-Item -Force $tmpo $p
    $conflicts.Add($p)
    $report.Add("CONFLICT`t$p (rc=$rc)")
  }
  Remove-Item -Force $tmpb, $tmpo, $tmpt -ErrorAction SilentlyContinue
}

$report | Set-Content F:\jni_merge_report.txt
"WORK=$work CONFLICTS=$($conflicts.Count)"
$conflicts | ForEach-Object { "C: $($_)" }
