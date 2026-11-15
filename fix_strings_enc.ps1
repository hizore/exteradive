$ErrorActionPreference = "Continue"
[Environment]::CurrentDirectory = "F:\exteraless"
Set-Location F:\exteraless
$BASE   = "b8eaea9087"
$OURS   = "dddc5198b2"
$THEIRS = "31a35e4683"
$gitPath = (Get-Command git.exe).Source
$utf8 = New-Object Text.UTF8Encoding($false)

function Get-BlobText($rev, $p) {
  $tmp = "$env:TEMP\\fx3_blob.bin"
  if (Test-Path $tmp) { Remove-Item $tmp -Force }
  cmd /c "`"$gitPath`" cat-file blob `"$($rev):$p`" > `"$tmp`"" 2>$null
  if (-not (Test-Path $tmp)) { return $null }
  return ($utf8.GetString([IO.File]::ReadAllBytes($tmp))).Replace("`r`n","`n")
}

$entryRx = [regex]'(?m)^[ \t]*<(string|plurals|string-array|style)\b[^>]*\bname="([^"]+)"[^>]*>(?s).*?</\1>[\t ]*$|^[ \t]*<(string|plurals|string-array|style)\b[^>]*\bname="([^"]+)"[^>]*/>[\t ]*$'
$nameRx = [regex]'\bname="([^"]+)"'
function Get-EntryMap($text) {
  $map = @{}
  foreach ($m in $entryRx.Matches($text)) {
    $nm = $nameRx.Match($m.Value).Groups[1].Value
    if ($nm -and -not $map.ContainsKey($nm)) { $map[$nm] = [Text.Encoding]::UTF8.GetBytes($m.Value) }
  }
  return $map
}
function HashOf($bytes) {
  return ([Security.Cryptography.SHA1]::Create().ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
}

$files = @(
  "TMessagesProj/src/main/res/values/strings.xml",
  "TMessagesProj/src/main/res/values/strings_oe_general.xml",
  "TMessagesProj/src/main/res/values-ru-rRU/strings_oe_general.xml"
)

foreach ($p in $files) {
  $theirsT = Get-BlobText $THEIRS $p
  $forkT   = Get-BlobText $OURS   $p
  $baseT   = Get-BlobText $BASE   $p
  $abs = Join-Path "F:\exteraless" $p
  $cur = ($utf8.GetString([IO.File]::ReadAllBytes($abs))).Replace("`r`n","`n")

  $theirsM = Get-EntryMap $theirsT
  $forkM   = Get-EntryMap $forkT
  $baseM   = Get-EntryMap $baseT
  $curM    = Get-EntryMap $cur

  # --- build WANT map (correct merge result per key) ---
  $want = @{}
  $bothChanged = New-Object System.Collections.Generic.List[string]
  $baseOnlyLeft = New-Object System.Collections.Generic.List[string]
  foreach ($nm in ($theirsM.Keys + $forkM.Keys | Sort-Object -Unique)) {
    $inT = $theirsM.ContainsKey($nm); $inF = $forkM.ContainsKey($nm)
    if ($inT -and -not $inF) { $want[$nm] = $theirsM[$nm]; continue }
    if (-not $inT -and $inF) { $want[$nm] = $forkM[$nm]; continue }
    $tH = HashOf $theirsM[$nm]; $fH = HashOf $forkM[$nm]
    if ($tH -eq $fH) { $want[$nm] = $theirsM[$nm]; continue }
    $bH = if ($baseM.ContainsKey($nm)) { HashOf $baseM[$nm] } else { $null }
    if ($null -ne $bH) {
      if ($fH -ne $bH -and $tH -eq $bH) { $want[$nm] = $forkM[$nm]; continue }      # fork tweak, upstream untouched
      if ($fH -eq $bH -and $tH -ne $bH) { $want[$nm] = $theirsM[$nm]; continue }    # upstream change only
      if ($fH -ne $bH -and $tH -ne $bH) {                                            # both changed -> keep side that matches cur
        $cH = if ($curM.ContainsKey($nm)) { HashOf $curM[$nm] } else { $null }
        if ($cH -eq $fH) { $want[$nm] = $forkM[$nm] } elseif ($cH -eq $tH) { $want[$nm] = $theirsM[$nm] }
        else { $want[$nm] = $theirsM[$nm]; $bothChanged.Add($nm) }
        continue
      }
    } else {
      # no base entry: added independently by fork and upstream
      $cH = if ($curM.ContainsKey($nm)) { HashOf $curM[$nm] } else { $null }
      if ($cH -eq $fH) { $want[$nm] = $forkM[$nm] } else { $want[$nm] = $theirsM[$nm]; $bothChanged.Add($nm) }
      continue
    }
    $want[$nm] = $theirsM[$nm]
  }
  foreach ($nm in $baseM.Keys) {
    if (-not $theirsM.ContainsKey($nm) -and -not $forkM.ContainsKey($nm) -and $curM.ContainsKey($nm)) { $baseOnlyLeft.Add($nm) }
  }
  $missingForkOnly = @($forkM.Keys | Where-Object { -not $theirsM.ContainsKey($_) -and -not $curM.ContainsKey($_) })

  # --- rewrite ---
  $replaced = 0; $unchanged = 0
  $new = $entryRx.Replace($cur, {
    param($m)
    $nm = $nameRx.Match($m.Value).Groups[1].Value
    if (-not $nm -or -not $want.ContainsKey($nm)) { return $m.Value }
    $wt = $utf8.GetString($want[$nm])
    if ($m.Value -eq $wt) { $script:unchanged++; return $m.Value }
    $script:replaced++
    return $wt
  })

  $afterCount = $entryRx.Matches($new).Count
  Write-Output ("$p : entries " + $curM.Count + " -> " + $afterCount + " replaced=" + $replaced + " unchanged=" + $unchanged + " bothChanged=" + $bothChanged.Count + " baseOnlyPresent=" + $baseOnlyLeft.Count + " forkOnlyMissing=" + $missingForkOnly.Count)
  if ($afterCount -lt $curM.Count) { Write-Output "  ABORT: count decreased"; continue }
  if ($bothChanged.Count -gt 0) { Write-Output ("  BOTH-CHANGED: " + (($bothChanged | Select-Object -First 15) -join ", ")) }
  if ($baseOnlyLeft.Count -gt 0) { Write-Output ("  BASE-ONLY-PRESENT: " + (($baseOnlyLeft | Select-Object -First 10) -join ", ")) }
  if ($missingForkOnly.Count -gt 0) { Write-Output ("  FORK-ONLY-MISSING: " + (($missingForkOnly | Select-Object -First 10) -join ", ")) }
  [IO.File]::WriteAllBytes($abs, $utf8.GetBytes($new))
}
Write-Output "FIX-DONE"
