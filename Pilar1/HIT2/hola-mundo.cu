#include <stdio.h>

__global__ void holaMundo() {
    printf("Hola Mundo desde GPU!\n");
}

int main() {
    holaMundo<<<1,1>>>();
    cudaDeviceSynchronize();
    return 0;
}
