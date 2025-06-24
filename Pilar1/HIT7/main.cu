// main.cu
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cuda_runtime.h>
#include "md5.cuh"

using byte = unsigned char;
using word = unsigned short;

// Rango de nonces a probar en cada batch (≤ 65 535)
static constexpr int BATCH_SIZE = 1 << 15;  // 32 768 nonces por llamada

int main(int argc, char** argv) {
    if (argc != 5) {
        std::fprintf(stderr, "Uso: %s <prefijo_hex> <cadena> <inicio> <fin>\n", argv[0]);
        return EXIT_FAILURE;
    }

    const char* prefix = argv[1];
    const char* base   = argv[2];
    int prefix_len = std::strlen(prefix);
    int base_len   = std::strlen(base);

    // Leer rango de búsqueda
    unsigned long inicio = std::strtoul(argv[3], nullptr, 10);
    unsigned long fin    = std::strtoul(argv[4], nullptr, 10);
    if (fin < inicio) {
        std::fprintf(stderr, "Error: fin debe ser mayor o igual a inicio\n");
        return EXIT_FAILURE;
    }

    // buffers host/device
    byte*  h_in   = nullptr;
    byte*  h_out  = nullptr;
    bool*   d_found = nullptr;
    int*    d_found_idx = nullptr;

    cudaHostAlloc(&h_in,    BATCH_SIZE * (base_len + 16), cudaHostAllocDefault);
    cudaHostAlloc(&h_out,   BATCH_SIZE * 16,             cudaHostAllocDefault);
    cudaMalloc   (&d_found, sizeof(bool));
    cudaMalloc   (&d_found_idx, sizeof(int));
    cudaMemset   (d_found, 0, sizeof(bool));

    unsigned long global_start = inicio;
    bool done = false;
    unsigned long found_nonce = (unsigned long)(-1);
    byte found_hash[16];

    while (!done && global_start <= fin) {
        // Ajustar el tamaño del batch si nos acercamos al fin del rango
        int this_batch = BATCH_SIZE;
        if (global_start + this_batch - 1 > fin) {
            this_batch = (int)(fin - global_start + 1);
        }

        // Preparar input batch
        for (int i = 0; i < this_batch; ++i) {
            unsigned long nonce = global_start + i;
            int len = std::sprintf(reinterpret_cast<char*>(h_in + i * (base_len + 16)),
                                   "%lu", nonce);
            std::memcpy(h_in + i * (base_len + 16) + len,
                        base, base_len);
            std::memset(h_in + i * (base_len + 16) + len + base_len,
                        0, 16 - len);
        }

        // Reset flag y llamar batch kernel
        cudaMemset(d_found, 0, sizeof(bool));
        mcm_cuda_md5_hash_batch(
          h_in,
          (word)(base_len + 16),
          h_out,
          (word)this_batch);

        // Buscar solución en resultados
        for (int i = 0; i < this_batch; ++i) {
            bool ok = true;
            for (int b = 0; b < prefix_len; ++b) {
                byte v = h_out[i * 16 + (b / 2)];
                char c = (b % 2 == 0)
                  ? "0123456789abcdef"[(v >> 4) & 0xF]
                  : "0123456789abcdef"[v & 0xF];
                if (c != prefix[b]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                done = true;
                found_nonce = global_start + i;
                memcpy(found_hash, h_out + i * 16, 16);
                break;
            }
        }

        global_start += this_batch;
    }

    if (done) {
        // imprimir resultado
        char hex[33] = {0};
        static const char* digs = "0123456789abcdef";
        for (int i = 0; i < 16; ++i) {
            hex[2 * i]     = digs[(found_hash[i] >> 4) & 0xF];
            hex[2 * i + 1] = digs[found_hash[i] & 0xF];
        }
        std::printf("Nonce encontrado: %lu\n", found_nonce);
        std::printf("Hash resultante: %s\n", hex);
    } else {
        std::printf("No se encontro un hash que comience con '%s' en el rango [%lu, %lu]\n",
                    prefix, inicio, fin);
    }

    // limpiar
    cudaFreeHost(h_in);
    cudaFreeHost(h_out);
    cudaFree(d_found);
    cudaFree(d_found_idx);

    return EXIT_SUCCESS;
}