$ErrorActionPreference = "Continue"
[Environment]::CurrentDirectory = "F:\exteraless"
Set-Location F:\exteraless
$BASE   = "b8eaea9087"
$OURS   = "dddc5198b2"
$THEIRS = "31a35e4683"
$gitPath = (Get-Command git.exe).Source
$utf8 = New-Object Text.UTF8Encoding($false)

$files = @(
  "TMessagesProj/src/main/res/values/strings.xml",
  "TMessagesProj/src/main/res/values/strings_oe_general.xml",
  "TMessagesProj/src/main/res/values-ru-rRU/strings_oe_general.xml"
)

function Get-BlobText($rev, $p) {
  $tmp = "$env:TEMP\\an_blob.bin"
  if (Test-Path $tmp) { Remove-Item $tmp -Force }
  cmd /c "`"$gitPath`" cat-file blob `"$($rev):$p`" > `"$tmp`"" 2>$null
  if (-not (Test-Path $tmp)) { return $null }
  return $utf8.GetString([IO.File]::ReadAllBytes($tmp))
}

$entryRx = [regex]'(?m)^[ \t]*<(string|plurals|string-array|style)\b[^>]*\bname="([^"]+)"[^>]*>(?s).*?</\1>[\t ]*$|^[ \t]*<(string|plurals|string-array|style)\b[^>]*\bname="([^"]+)"[^>]*/>[\t ]*$'
$nameRx = [regex]'\bname="([^"]+)"'
function Get-EntryMap($text) {
  $map = @{}
  foreach ($m in $entryRx.Matches($text)) {
    $nm = $nameRx.Match($m.Value).Groups[1].Value
    if ($nm -and -not $map.ContainsKey($nm)) {
      $map[$nm] = [Text.Encoding]::UTF8.GetBytes($m.Value)
    }
  }
  return $map
}
$sha = New-Object Security.Cryptography.SHA1CryptoServiceProvider
function HashOf($bytes) {
  return ([Security.Cryptography.SHA1]::Create().ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
}
$boxRx = "[\u2500-\u25FF]"

foreach ($p in $files) {
  $theirsT = Get-BlobText $THEIRS $p; $theirsT = $theirsT.Replace("`r`n","`n")
  $forkT   = Get-BlobText $OURS   $p; $forkT   = $forkT.Replace("`r`n","`n")
  $baseT   = Get-BlobText $BASE   $p; $baseT   = $baseT.Replace("`r`n","`n")
  $curT = $utf8.GetString([IO.File]::ReadAllBytes((Join-Path "F:\exteraless" $p))).Replace("`r`n","`n")

  $theirsM = Get-EntryMap $theirsT
  $forkM   = Get-EntryMap $forkT
  $baseM   = Get-EntryMap $baseT
  $curM    = Get-EntryMap $curT

  $cEqTheirs=0; $cEqFork=0; $cEqBase=0; $cOther=0
  $other = New-Object System.Collections.Generic.List[string]
  $forkTweaks = 0; $forkTweakList = New-Object System.Collections.Generic.List[string]
  $theirsBoxes = 0
  foreach ($nm in $curM.Keys) {
    $cv = $curM[$nm]
    $h = HashOf $cv
    if ($theirsM.ContainsKey($nm)) {
      $th = HashOf $theirsM[$nm]
      if (([regex]::Matches($utf8.GetString($theirsM[$nm]), $boxRx)).Count -gt 0) { $theirsBoxes++ }
      if ($h -eq $th) { $cEqTheirs++; continue }
    }
    if ($forkM.ContainsKey($nm) -and $h -eq (HashOf $forkM[$nm])) { $cEqFork++; continue }
    if ($baseM.ContainsKey($nm) -and $h -eq (HashOf $baseM[$nm])) { $cEqBase++; continue }
    $cOther++
    if ($other.Count -lt 40) { $other.Add($nm) }
  }
  foreach ($nm in $theirsM.Keys) {
    if ($forkM.ContainsKey($nm) -and (HashOf $forkM[$nm]) -ne (HashOf $theirsM[$nm])) {
      if (-not $baseM.ContainsKey($nm) -or (HashOf $baseM[$nm]) -ne (HashOf $forkM[$nm])) {
        $forkTweaks++
        if ($forkTweakList.Count -lt 20) { $forkTweakList.Add($nm) }
      }
    }
  }
  Write-Output ("FILE: " + $p)
  Write-Output ("  curEntries=" + $curM.Count + " cur==theirs:" + $cEqTheirs + " cur==fork:" + $cEqFork + " cur==base:" + $cEqBase + " OTHER:" + $cOther)
  Write-Output ("  theirsEntriesWithBoxes=" + $theirsBoxes + "  forkTweaksOnShared=" + $forkTweaks)
  Write-Output ("  OTHER first: " + (($other | Select-Object -First 25) -join ", "))
  Write-Output ("  FORK-TWEAKS first: " + (($forkTweakList | Select-Object -First 15) -join ", "))
}
Write-Output "ANALYSIS-DONE"
