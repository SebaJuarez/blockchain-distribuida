# Hit #7 - HASH por fuerza bruta con CUDA (con límites)
Modifique el programa anterior para que reciba dos parámetros nuevos, ambos serán números y su programa debe buscar posibles soluciones solo dentro de ese rango, si en ese rango no hay soluciones debe informar que no encontró nada.

## Compilación y ejecución

```sh
nvcc -O3 main.cu md5.cu -o md5_cuda
md5_cuda <prefijo_hex> <cadena_base> <inicio> <fin>
```