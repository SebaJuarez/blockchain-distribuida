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
    if (argc != 3) {
        std::fprintf(stderr, "Uso: %s <prefijo_hex> <cadena>\n", argv[0]);
        return EXIT_FAILURE;
    }

    const char* prefix = argv[1];
    const char* base   = argv[2];
    int prefix_len = std::strlen(prefix);
    int base_len   = std::strlen(base);

    // buffers host/device
    // h_in: para enviar BATCH_SIZE concatenaciones de (nonce + base), todas con igual longitud
    // h_out: para recuperar BATCH_SIZE hashes (16 bytes c/u)
    // d_found: flag para indicar cuándo se encontró
    byte*  h_in   = nullptr;
    byte*  h_out  = nullptr;
    bool   *d_found = nullptr;
    int    *d_found_idx = nullptr;

    cudaHostAlloc(&h_in,    BATCH_SIZE * (base_len + 16), cudaHostAllocDefault);
    cudaHostAlloc(&h_out,   BATCH_SIZE * 16,             cudaHostAllocDefault);
    cudaMalloc   (&d_found, sizeof(bool));
    cudaMalloc   (&d_found_idx, sizeof(int));
    cudaMemset   (d_found, 0, sizeof(bool));

    int  global_start = 0;
    bool done = false;
    int  found_nonce = -1;
    byte found_hash[16];

    while (!done) {
        // 1) Prepárate BATCH_SIZE nonces [global_start .. global_start + BATCH_SIZE)
        int this_batch = BATCH_SIZE;
        // (no ajustamos si sobrepasa, simplificamos; podría adaptarse)
        for (int i = 0; i < this_batch; ++i) {
            int nonce = global_start + i;
            // escribe nonce como string
            int len = std::sprintf(reinterpret_cast<char*>(h_in + i*(base_len+16)),
                                   "%d", nonce);
            // rellena con la base
            std::memcpy(h_in + i*(base_len+16) + len,
                        base, base_len);
            // ceros al final
            std::memset(h_in + i*(base_len+16) + len + base_len,
                        0, 16 - len);
        }

        // 2) copia los datos y el flag a device
        cudaMemset(d_found, 0, sizeof(bool));
        cudaMemcpyToSymbol(/*mcm_cuda_md5_input*/ nullptr, nullptr, 0); // no-op
        // Llamada batch: internamente ejecuta un kernel de BATCH_SIZE hilos
        mcm_cuda_md5_hash_batch(
          h_in,
          (word)(base_len + 16),   // longitud fija de cada mensaje (_nonce_str + base)
          h_out,
          (word)this_batch);

        // 3) después de la llamada, escanea en h_out si alguno cumple el prefijo
        for (int i = 0; i < this_batch; ++i) {
            // convierte los primeros prefix_len nibbles a chars
            bool ok = true;
            for (int b = 0; b < prefix_len; ++b) {
                // cada byte de hash → 2 hex chars
                byte v = h_out[i*16 + (b/2)];
                char c = (b%2==0)
                  ? "0123456789abcdef"[(v>>4)&0xF]
                  : "0123456789abcdef"[v&0xF];
                if (c != prefix[b]) { ok = false; break; }
            }
            if (ok) {
                done = true;
                found_nonce = global_start + i;
                memcpy(found_hash, h_out + i*16, 16);
                break;
            }
        }

        global_start += this_batch;
    }

    // Imprime resultado
    // hash en hex:
    char hex[33] = {0};
    static const char* digs = "0123456789abcdef";
    for (int i = 0; i < 16; ++i) {
        hex[2*i  ] = digs[(found_hash[i] >> 4) & 0xF];
        hex[2*i+1] = digs[found_hash[i] & 0xF];
    }
    std::printf("Nonce encontrado: %d\n", found_nonce);
    std::printf("Hash resultante: %s\n", hex);

    // limpia
    cudaFreeHost(h_in);
    cudaFreeHost(h_out);
    cudaFree(d_found);
    cudaFree(d_found_idx);
    return EXIT_SUCCESS;
}