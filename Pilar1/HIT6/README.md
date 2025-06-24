# Hit #6 - Longitudes de prefijo en CUDA HASH
Realice mediciones sobre el programa anterior probando diferentes longitudes de prefijo. ¿Cuál es el prefijo más largo que logró encontrar? ¿Cuánto tardo? ¿Cuál es la relación entre la longitud del prefijo a buscar y el tiempo requerido para encontrarlo?

### Script de prueba
Se realizaron mediciones de tiempo sobre el programa del Hit #5, variando la longitud del prefijo.

Se escribió un script de automatización en PowerShell que ejecuta el binario varias veces, incrementando la longitud del prefijo desde 1 hasta 10 (es decir, desde `0` hasta `0000000000`) y midiendo el tiempo de ejecución de cada búsqueda.

Cada ejecución:
- Invoca el binario `md5_cuda.exe` con el prefijo deseado y un texto base fijo.
- Usa `Measure-Command` para calcular el tiempo que tarda en encontrar un nonce válido.
- Imprime el tiempo total por prefijo.

---

### Resultados

| Prefijo      | Longitud         | Tiempo (s) |
|--------------|------------------|------------|
| `0`          | 1                | 0,3139     |
| `00`         | 2                | 0,1441     |
| `000`        | 3                | 0,1521     |
| `0000`       | 4                | 0,1476     |
| `00000`      | 5                | 0,2009     |
| `000000`     | 6                | 1,0105     |
| `0000000`    | 7                | 3,7684     |
| `00000000`   | 8                | 309.6637   |

---

### Estimación para 9 ceros (`000000000`, 36 bits)

Dado que para 32 bits (8 ceros) tomó aproximadamente **310 segundos**, se puede estimar que:

- Rendimiento observado ≈ 2³² / 310 ≈ 13.8 millones de hashes/segundo
- Tiempo estimado para 36 bits:  
  2³⁶ / (13.8 × 10⁶) ≈ **4976 segundos ≈ 83 minutos**

Esto indica que sí es **posible** encontrar un hash con 9 ceros iniciales, pero requiere **cerca de 1 hora y media** de ejecución continua.

---

### Conclusiones

- El tiempo de búsqueda crece exponencialmente con la longitud del prefijo.
- La GPU permite explorar espacios grandes de búsqueda rápidamente hasta cierto punto (≈32 bits).
- Para prefijos mayores a 32 bits se requiere mucho tiempo.