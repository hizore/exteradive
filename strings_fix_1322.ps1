$ErrorActionPreference = "Stop"
Set-Location F:\exteraless
$OURS = "dddc5198b2"
$THEIRS = "31a35e4683"

$files = @(
  "TMessagesProj/src/main/res/values/strings.xml",
  "TMessagesProj/src/main/res/values/strings_oe_general.xml",
  "TMessagesProj/src/main/res/values-ru-rRU/strings_oe_general.xml"
)

$rx = [regex]'<(string|plurals|string-array|style)\b[^>]*\bname="([^"]+)"'
foreach ($p in $files) {
  git show "$($THEIRS):$p" > "$env:TEMP\sgt.bin"
  git show "$($OURS):$p" > "$env:TEMP\sgo.bin"
  $theirText = [IO.File]::ReadAllText("$env:TEMP\sgt.bin", [System.Text.Encoding]::UTF8)
  $oursText  = [IO.File]::ReadAllText("$env:TEMP\sgo.bin", [System.Text.Encoding]::UTF8)
  # fork keys present in ours, absent in theirs
  $tk = @{}
  foreach ($m in $rx.Matches($theirText)) { $tk[$m.Groups[2].Value] = $m.Groups[1].Value }
  $ok = @{}
  foreach ($m in $rx.Matches($oursText)) { $ok[$m.Groups[2].Value] = $m.Groups[1].Value }
  $tail = ""
  $added = 0
  foreach ($k in ($ok.Keys | Sort-Object)) {
    if (-not $tk.ContainsKey($k)) {
      $tag = $ok[$k]
      $m = [regex]::Match($oursText, "    <$tag\b[^>]*\bname=`"$k`"[^>]*>(?s).*?</$tag>|    <$tag\b[^>]*\bname=`"$k`"[^>]*/>")
      if ($m.Success) { $tail += $m.Value + "`n"; $added++ } else { Write-Host "NOMATCH $k" }
    }
  }
  # sanity: theirs must have no duplicate keys
  $count = @{}
  foreach ($m in $rx.Matches($theirText)) { $count[$m.Groups[2].Value] = 1 + [int]$count[$m.Groups[2].Value] }
  $dups = $count.Keys | Where-Object { $count[$_] -gt 1 }
  Write-Host "$p : added=$added theirsDups=$($dups.Count)"
  $close = $theirText.LastIndexOf("</resources>")
  $out = $theirText.Substring(0, $close).TrimEnd() + "`n" + $tail + "</resources>`n"
  [IO.File]::WriteAllText((Resolve-Path $p), $out, (New-Object System.Text.UTF8Encoding($false)))
}
Write-Host DONE
