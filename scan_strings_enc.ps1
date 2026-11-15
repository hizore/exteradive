$ErrorActionPreference = "Continue"
Set-Location F:\exteraless
$cp1251 = [Text.Encoding]::GetEncoding(1251)
$utf8 = New-Object Text.UTF8Encoding($false)

$boxRx = "[\u2500-\u25FF]"   # box-drawing block: tell-tale of CP1251-mangled UTF-8
$files = Get-ChildItem "TMessagesProj\src\main\res" -Recurse -Filter "strings*.xml" | ForEach-Object { $_.FullName }
$affected = @()
foreach ($f in $files) {
  $text = $utf8.GetString([IO.File]::ReadAllBytes($f))
  $count = ([regex]::Matches($text, $boxRx)).Count
  if ($count -lt 3) { continue }
  $bytes = $cp1251.GetBytes($text)
  $repaired = $utf8.GetString($bytes)
  $bad = ([regex]::Matches($repaired, "\uFFFD")).Count
  if ($bad -gt 0) { Write-Output ("SKIP-BAD(" + $bad + ") " + $f); continue }
  $cyr = ([regex]::Matches($repaired, "[\u0430-\u044F\u0410-\u042F]")).Count
  if ($cyr -gt 5) {
    $affected += $f
    Write-Output ($f + "  marks=" + $count + " cyr=" + $cyr)
  } else {
    Write-Output ("NO-CYR(" + $cyr + ") " + $f)
  }
}
Write-Output ("AFFECTED: " + $affected.Count)
$affected | Set-Content (Join-Path $env:TEMP "affected_strings.txt")
