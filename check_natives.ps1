$ErrorActionPreference = "Stop"
$decls = Get-Content F:\java_natives.txt | Where-Object { $_ -match "^(.+) :: (.+)$" }
$syms = Get-Content F:\native_syms_all.txt
$symSet = @{}
foreach ($s in $syms) {
  # format: 000123 T Java_org_telegram_messenger_Class_nativeMethod
  $parts = $s -split "\s+", 3
  if ($parts.Count -ge 3) { $symSet[$parts[2]] = 1 }
}
$rx = [regex]'^(?:(?:public|private|protected|static|final|synchronized|abstract)\s+)*native\s+(?:[\w<>\[\],\s\.]+?)\s+(\w+)\s*\('
$rxDecl = [regex]'native\s+[\w<>\[\],\.\s]+\s+(\w+)\s*\('
$results = New-Object System.Collections.Generic.List[string]
foreach ($d in $decls) {
  $sep = $d.IndexOf(" :: ")
  $file = $d.Substring(0, $sep)
  $decl = $d.Substring($sep + 4)
  $m = $rxDecl.Match($decl)
  if (-not $m.Success) { continue }
  $mname = $m.Groups[1].Value
  # class package from file path
  $rel = $file -replace "^TMessagesProj/src/main/java/", ""
  $pkg = ($rel -replace "/[^/]+$", "") -replace "/", "."
  $cls = [IO.Path]::GetFileNameWithoutExtension($file) -replace "\.java$", ""
  # inner class: file Class.java contains inner classes; declarations like Class$Inner handled by outer symbol prefix
  $sym = "Java_" + ($pkg + "." + $cls).Replace(".", "_").Replace('$', '_') + "_" + $mname
  if (-not $symSet.ContainsKey($sym)) {
    # try without _1 overload suffixes variants
    $results.Add("$file :: $mname  [missing: $sym]")
  }
}
$results | Set-Content F:\native_missing.txt
"MISSING=$($results.Count)"
$results | Select-Object -First 60 | ForEach-Object { "$($_)" }
