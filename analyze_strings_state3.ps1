$ErrorActionPreference = "Continue"
[Environment]::CurrentDirectory = "F:\exteraless"
Set-Location F:\exteraless
$BASE   = "b8eaea9087"
$OURS   = "dddc5198b2"
$THEIRS = "31a35e4683"
$gitPath = (Get-Command git.exe).Source
$utf8 = New-Object Text.UTF8Encoding($false)
$lat = [Text.Encoding]::GetEncoding(28591)

function Get-BlobText($rev, $p) {
  $tmp = "$env:TEMP\\an3_blob.bin"
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
function HexHead($bytes, $n) {
  $c = [Math]::Min($n, $bytes.Length)
  $s = $lat.GetString($bytes, 0, $c)
  return (($s.ToCharArray() | Select-Object -First $c | ForEach-Object { "{0:X2}" -f [int]$_ }) -join " ")
}

$checks = @(
  @{ p = "TMessagesProj/src/main/res/values/strings.xml"; keys = @("AutodownloadPrivateChats","AddSubscriber","TelegramFeatures","AutodownloadSizeLimit") },
  @{ p = "TMessagesProj/src/main/res/values/strings_oe_general.xml"; keys = $null }
)

foreach ($c in $checks) {
  $p = $c.p
  $theirsM = Get-EntryMap (Get-BlobText $THEIRS $p)
  $forkM   = Get-EntryMap (Get-BlobText $OURS   $p)
  $baseM   = Get-EntryMap (Get-BlobText $BASE   $p)
  $curM    = Get-EntryMap ($utf8.GetString([IO.File]::ReadAllBytes((Join-Path "F:\exteraless" $p)))).Replace("`r`n","`n")
  Write-Output ("=== " + $p)

  # fork-only keys present in fork but missing in cur?
  $missing = New-Object System.Collections.Generic.List[string]
  foreach ($nm in $forkM.Keys) {
    if (-not $theirsM.ContainsKey($nm) -and -not $curM.ContainsKey($nm)) { $missing.Add($nm) }
  }
  Write-Output ("fork-only missing in cur: " + $missing.Count + ($missing | Select-Object -First 12 | ForEach-Object { " " + $_ }))

  if ($c.keys) { $tweakKeys = $c.keys }
  else {
    $tweakKeys = @()
    foreach ($nm in $theirsM.Keys) {
      if ($forkM.ContainsKey($nm) -and (HashOf $forkM[$nm]) -ne (HashOf $theirsM[$nm])) {
        if (-not $baseM.ContainsKey($nm) -or (HashOf $baseM[$nm]) -ne (HashOf $forkM[$nm])) { $tweakKeys += $nm }
      }
    }
  }
  foreach ($nm in $tweakKeys) {
    $tH = if ($theirsM.ContainsKey($nm)) { HashOf $theirsM[$nm] } else { "-" }
    $fH = if ($forkM.ContainsKey($nm))   { HashOf $forkM[$nm] }   else { "-" }
    $bH = if ($baseM.ContainsKey($nm))   { HashOf $baseM[$nm] }   else { "-" }
    $cH = if ($curM.ContainsKey($nm))    { HashOf $curM[$nm] }    else { "-" }
    $cls = if ($cH -eq $tH) { "cur==THEIRS" } elseif ($cH -eq $fH) { "cur==FORK" } elseif ($cH -eq $bH) { "cur==BASE" } else { "cur==OTHER" }
    $tb = if ($bH -ne "-" -and $tH -eq $bH) { " theirs==base" } else { "" }
    Write-Output ("  TWEAK " + $nm + ": " + $cls + $tb)
  }
}
Write-Output "ANALYSIS3-DONE"
