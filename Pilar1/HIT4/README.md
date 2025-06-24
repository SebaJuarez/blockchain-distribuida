# Hit #4 - Introducción a HASH usando CUDA
El cálculo de funciones de hashing es ampliamente utilizado en criptografía, existen múltiples algoritmos, algunos como md5 (1991) ya son considerados inseguros y otros como sha256 (2001-2002) aún resisten la evolución y los tiempos actuales. Estos algoritmos suelen ser calculados en GPU ya que una de sus características es que son “costosos” de calcular computacionalmente.
En este punto, usted deberá escribir un programa que reciba un string por parámetro y calcule, utilizando la GPU, un md5 y devuelve el hash calculado por consola.
Puede usar librerías disponibles para este fin. Las encontrará preguntando por CUDA MD5.

## Librería utilizada
Programa en CUDA/C++ que calcula múltiples hashes MD5 de manera paralela en GPU, usando la librería [cuda-hashing-algos](https://github.com/mochimodev/cuda-hashing-algos).

### Características

- Calcula MD5 de N cadenas simultáneamente en GPU.
- Rellena automáticamente cadenas de distinta longitud al máximo.
- Internamente lanza kernels con 256 hilos por bloque.
- Optimizado con `-O3` tanto en host como en device.

## Compilación y ejecución

```sh
nvcc -O3 main.cu md5.cu -o md5_cuda
md5_cuda "hola mundo, esta es una prueba"
```