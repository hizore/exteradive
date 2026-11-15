$ErrorActionPreference = "Continue"
Set-Location F:\exteraless
function Get-NativeDecls($rev, $outFile) {
  $lines = @(git grep -n "native " $rev -- TMessagesProj/src/main/java 2>$null)
  $out = New-Object System.Collections.Generic.List[string]
  $rx = [regex]'native\s+[\w<>\[\],\.\s]+\s+(\w+)\s*\(([^)]*)\)'
  foreach ($l in $lines) {
    if (-not $l) { continue }
    if ($l -notmatch "native [A-Za-z]") { continue }
    $idx = $l.IndexOf(":")
    $rest = $l.Substring($idx + 1)
    $idx2 = $rest.IndexOf(":")
    $path = $rest.Substring(0, $idx2)
    $code = $rest.Substring($idx2 + 1)
    $m = $rx.Match($code)
    if (-not $m.Success) { continue }
    $name = $m.Groups[1].Value
    $args = $m.Groups[2].Value
    $argCount = 0
    if ($args.Trim().Length -gt 0) { $argCount = ($args -split ",").Count }
    $out.Add("$path :: $name($argCount)")
  }
  $out | Sort-Object -Unique | Set-Content $outFile
}
Get-NativeDecls "dddc5198b2" F:\natives_old.txt
Get-NativeDecls "HEAD" F:\natives_new.txt
$old = @{}
Get-Content F:\natives_old.txt | ForEach-Object { $old[$_] = 1 }
$new = @{}
Get-Content F:\natives_new.txt | ForEach-Object { $new[$_] = 1 }
"--- removed/changed (in old, absent in new) ---"
$old.Keys | Where-Object { -not $new.ContainsKey($_) } | Sort-Object | ForEach-Object { "$($_)" }
"--- added (in new, absent in old) ---"
$added = $new.Keys | Where-Object { -not $old.ContainsKey($_) } | Sort-Object
"ADDED-COUNT=$($added.Count)"
$added | ForEach-Object { "$($_)" }
