# Parámetros configurables
$exe = "../HIT5/md5_cuda.exe"
$base = "benchmark"
$maxLength = 10 
$outCsv = "hit6_results.csv"

# Cabecera CSV
"length, seconds" | Out-File $outCsv

for ($L = 1; $L -le $maxLength; $L++) {
    # Construye un prefijo de L ceros: e.g. "0000"
    $pref = '0' * $L

    # Cronometra la ejecución completa
    $time = Measure-Command { & $exe $pref $base }

    # Extrae los segundos totales (de tipo [TimeSpan])
    $sec = [math]::Round($time.TotalSeconds, 4)

    # Muestra en pantalla
    Write-Host ("Prefijo: {0} (len={1}) → {2} s" -f $pref, $L, $sec)

    # Apendea al CSV
    "{0},{1}" -f $L, $sec | Out-File $outCsv -Append
}

Write-Host "`nResultados escritos en $outCsv"