$ErrorActionPreference = "Continue"
[Environment]::CurrentDirectory = "F:\exteraless"
Set-Location F:\exteraless
$BASE   = "b8eaea9087"
$OURS   = "dddc5198b2"
$THEIRS = "31a35e4683"
$gitPath = (Get-Command git.exe).Source
$utf8 = New-Object Text.UTF8Encoding($false)

function Get-BlobText($rev, $p) {
  $tmp = "$env:TEMP\\an2_blob.bin"
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
$boxRx = "[\u2500-\u25FF]"

$files = @(
  "TMessagesProj/src/main/res/values/strings.xml",
  "TMessagesProj/src/main/res/values/strings_oe_general.xml",
  "TMessagesProj/src/main/res/values-ru-rRU/strings_oe_general.xml"
)

foreach ($p in $files) {
  $theirsM = Get-EntryMap (Get-BlobText $THEIRS $p)
  $forkM   = Get-EntryMap (Get-BlobText $OURS   $p)
  $baseM   = Get-EntryMap (Get-BlobText $BASE   $p)
  $curM    = Get-EntryMap ($utf8.GetString([IO.File]::ReadAllBytes((Join-Path "F:\exteraless" $p)))).Replace("`r`n","`n")

  Write-Output ("=== " + $p)
  $inTheirs=0; $inFork=0; $inBoth=0; $inNone=0
  $mangledTheir=0; $mangledForkOnly=0; $otherList = New-Object System.Collections.Generic.List[string]
  foreach ($nm in $curM.Keys) {
    $cv = $curM[$nm]; $h = HashOf $cv
    $tH = if ($theirsM.ContainsKey($nm)) { HashOf $theirsM[$nm] } else { $null }
    $fH = if ($forkM.ContainsKey($nm))   { HashOf $forkM[$nm] }   else { $null }
    $bH = if ($baseM.ContainsKey($nm))   { HashOf $baseM[$nm] }   else { $null }
    if ($h -eq $tH -or $h -eq $fH) { continue }
    if ($tH) { $inTheirs++ } elseif ($fH) { $inFork++ } elseif ($bH) { $inBoth++ } else { $inNone++ }
    $hasBox = ([regex]::Matches($utf8.GetString($cv), $boxRx)).Count -gt 0
    if ($tH -and $hasBox) { $mangledTheir++ }
    elseif ($fH -and $hasBox -and -not $tH) { $mangledForkOnly++ }
    if ($otherList.Count -lt 60) {
      $src = if ($tH) { "theirs" } elseif ($fH) { "fork" } elseif ($bH) { "base-only" } else { "NONE" }
      $twk = ""
      if ($fH -and $tH -and $fH -ne $tH -and (-not $bH -or (HashOf $baseM[$nm]) -ne $fH)) { $twk = " FORK-TWEAK(theirs==base:" + ($tH -eq $bH) + ")" }
      $otherList.Add("  OTHER[$src]$twk " + $nm)
    }
  }
  Write-Output ("OTHER total-in-theirs=" + $inTheirs + " fork-only=" + $inFork + " base-only=" + $inBoth + " none=" + $inNone + " mangled-theirs=" + $mangledTheir + " mangled-forkonly=" + $mangledForkOnly)
  $otherList | ForEach-Object { Write-Output $_ }
}
Write-Output "ANALYSIS2-DONE"
