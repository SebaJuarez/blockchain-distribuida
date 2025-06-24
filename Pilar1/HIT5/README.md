# Hit #5 - HASH por fuerza bruta con CUDA
Modifique el programa anterior para que reciba dos parámetros (un hash y una cadena). Ahora debe encontrar un número tal que, al concatenarlo con la cadena y calcular el hash, el resultado comience con una cadena específica proporcionada como segundo parámetro. 
Como no hay forma de adivinar cuál es ese número, deberá utilizar la GPU para probar miles o millones de combinaciones por segundo aleatoriamente hasta encontrar la correcta.
Como salida, debe mostrar el hash resultante y el número que utilizó para generarlo.

## Funcionamiento

1. Genera bloques de nonces de tamaño fijo (`BATCH_SIZE`).
2. Empaqueta en memoria page-locked las cadenas `nonce + base` con longitud uniforme.
3. Llama a `mcm_cuda_md5_hash_batch` para calcular en paralelo en GPU decenas de miles de hashes.
4. Escanea los resultados en CPU hasta encontrar el primer hash que coincida con el prefijo.
5. Imprime el nonce y el hash completo en hexadecimal.

## Compilación y ejecución

```sh
nvcc -O3 main.cu md5.cu -o md5_cuda
md5_cuda <prefijo_hex> "<cadena>"
```
- `<prefijo_hex>`: cadena hexa objetivo con la que debe comenzar el hash (ej. `0000`).
- `<cadena>`: texto base al que se concatena el nonce.

**Ejemplo**:

```sh
md5_cuda 0000 "hola mundo"
# Nonce encontrado: 104857
# Hash resultante: 0000ab12cd34ef56...7890abcd
```

Este Hit aprovecha la GPU para probar millones de combinaciones por segundo reutilizando la infraestructura de cálculo de MD5 por *batches* ya desarrollada.
