$ErrorActionPreference = "Continue"
Set-Location F:\exteraless
$OURS = "dddc5198b2"     # форк до обновления (форк-строки)
$THEIRS = "31a35e4683"   # upstream 13.2.2
$gitPath = (Get-Command git.exe).Source

function BlobSha($rev, $path) {
  $l = git ls-tree $rev -- $path 2>$null
  if (-not $l) { return $null }
  $tab = $l.IndexOf("`t")
  if ($tab -lt 0) { return $null }
  $meta = $l.Substring(0, $tab)
  $mp = $meta -split " ", 3
  if ($mp[1] -eq "commit") { return $null }
  return $mp[2]
}

$rx = [regex]'<(string|plurals|string-array|style)\b[^>]*\bname="([^"]+)"'
function KeySet($text) {
  $keys = @{}
  foreach ($m in $rx.Matches($text)) { $keys[$m.Groups[2].Value] = $m.Groups[1].Value }
  return $keys
}

$files = git diff --name-only $OURS $THEIRS -- "TMessagesProj/src/main/res/values" "TMessagesProj/src/main/res/values-*"
$fixed = 0
$report = New-Object System.Collections.Generic.List[string]
foreach ($p in $files) {
  if ($p -like "*strings_oe_plugins*") { continue }
  if (-not (Test-Path $p)) { $report.Add("MISSING-WORKTREE`t$p"); continue }
  $oursSha = BlobSha $OURS $p
  $theirSha = BlobSha $THEIRS $p
  if (-not $oursSha -or -not $theirSha) { $report.Add("SHA-MISS`t$p"); continue }
  cmd /c "`"$gitPath`" show `"$($OURS):$p`" > `"$env:TEMP\sg_ours.bin`"" 2>$null
  cmd /c "`"$gitPath`" show `"$($THEIRS):$p`" > `"$env:TEMP\sg_theirs.bin`"" 2>$null
  if (-not ((Test-Path "$env:TEMP\sg_ours.bin") -and (Test-Path "$env:TEMP\sg_theirs.bin"))) { $report.Add("SHOW-FAIL`t$p"); continue }
  $oursText = [IO.File]::ReadAllText("$env:TEMP\sg_ours.bin", [System.Text.Encoding]::UTF8)
  $theirText = [IO.File]::ReadAllText("$env:TEMP\sg_theirs.bin", [System.Text.Encoding]::UTF8)
  $kb = KeySet $oursText
  $kt = KeySet $theirText
  $cur = [IO.File]::ReadAllText((Resolve-Path $p), [System.Text.Encoding]::UTF8)
  $added = 0
  $tail = ""
  $union = @{}
  foreach ($k in $kb.Keys) { $union[$k] = "ours" }
  foreach ($k in $kt.Keys) { if (-not $union.ContainsKey($k)) { $union[$k] = "theirs" } }
  foreach ($k in ($union.Keys | Sort-Object)) {
    if ($cur -notmatch [regex]::Escape("name=`"$k`"")) {
      $tag = if ($kb.ContainsKey($k)) { $kb[$k] } else { $kt[$k] }
      $src = if ($kb.ContainsKey($k)) { $oursText } else { $theirText }
      $m = [regex]::Match($src, "<$tag\b([^>]*)>(?s).*?</$tag>|<$tag\b[^>]*name=`"$k`"[^>]*/>")
      if ($m.Success) {
        $tail += "    " + $m.Value + "`n"
        $added++
      }
    }
  }
  if ($added -gt 0) {
    $close = $cur.LastIndexOf("</resources>")
    if ($close -ge 0) {
      $cur = $cur.Substring(0, $close) + $tail + $cur.Substring($close)
      [IO.File]::WriteAllText((Resolve-Path $p), $cur, (New-Object System.Text.UTF8Encoding($false)))
      $fixed++
      $report.Add("RESTORED`t$p`tkeys=$added")
    } else {
      $report.Add("NO-RESOURCES-TAG`t$p")
    }
  } else {
    $report.Add("OK`t$p")
  }
}
$report | Set-Content -Encoding utf8 F:\strings_guard_report.txt
$report | Group-Object { ($_ -split "`t")[0] } | ForEach-Object { Write-Host "$($_.Name) = $($_.Count)" }
Write-Host "fixed=$fixed"
