#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <algorithm>
#include <cuda_runtime.h>
#include "md5.cuh"

using byte = unsigned char;
using word = unsigned short;

int main(int argc, char** argv) {
    if (argc < 2) {
        std::fprintf(stderr, "Uso: %s <texto1> [texto2 ... textoN]\n", argv[0]);
        return EXIT_FAILURE;
    }

    int n = argc - 1;
    std::vector<const char*> inputs(n);
    for (int i = 0; i < n; ++i) inputs[i] = argv[i+1];

    // 1) Determinar la longitud máxima
    size_t maxlen = 0;
    for (auto s : inputs) maxlen = std::max(maxlen, std::strlen(s));

    // 2) Crear buffer de entrada: n mensajes de longitud `maxlen`, padded con 0
    byte* h_in = (byte*)malloc(maxlen * n);
    for (int i = 0; i < n; ++i) {
        size_t L = std::strlen(inputs[i]);
        std::memcpy(h_in + i*maxlen, inputs[i], L);
        if (L < maxlen) std::memset(h_in + i*maxlen + L, 0, maxlen - L);
    }

    // 3) Reservar salida host (16 bytes de digest por mensaje)
    byte* h_out = (byte*)malloc(16 * n);

    // 4) Llamar a la función batch (gestiona cudaMalloc, kernel y cudaMemcpy)
    //    Parámetros: (in, length de CADA mensaje, out, cuántos mensajes)
    mcm_cuda_md5_hash_batch(h_in, (word)maxlen, h_out, (word)n);

    // 5) Imprimir resultados
    for (int i = 0; i < n; ++i) {
        std::printf("MD5(\"%s\") = ", inputs[i]);
        byte* digest = h_out + 16*i;
        for (int b = 0; b < 16; ++b) {
            std::printf("%02x", digest[b]);
        }
        std::printf("\n");
    }

    // 6) Liberar
    free(h_in);
    free(h_out);
    return EXIT_SUCCESS;
}