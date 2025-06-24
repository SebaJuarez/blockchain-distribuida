// main.cu
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string> // Para std::string y std::to_string
#include <cuda_runtime.h>
#include "md5.cuh" // Asegúrate de que esta cabecera esté disponible

using byte = unsigned char;
using word = unsigned int; // Asegúrate de que 'word' sea unsigned int aquí también

// Rango de nonces a probar en cada batch (≤ 65 535)
static constexpr int BATCH_SIZE = 1 << 15;  // 32 768 nonces por llamada

int main(int argc, char** argv) {
    if (argc != 5) {
        std::fprintf(stderr, "Uso: %s <prefijo_hex> <cadena> <inicio> <fin>\n", argv[0]);
        return EXIT_FAILURE;
    }

    const char* prefix = argv[1];
    const std::string base = argv[2]; // Usar std::string para facilitar la concatenación
    int prefix_len = std::strlen(prefix);
    int base_len = base.length();

    // Leer rango de búsqueda
    unsigned long inicio = std::strtoul(argv[3], nullptr, 10);
    unsigned long fin = std::strtoul(argv[4], nullptr, 10);
    if (fin < inicio) {
        std::fprintf(stderr, "Error: fin debe ser mayor o igual a inicio\n");
        return EXIT_FAILURE;
    }

    // Máxima longitud posible para una entrada (longitud máxima de nonce string + longitud de base)
    // Un unsigned long puede tener hasta 20 dígitos (para 18.4 quintillones)
    // Se inicializa después de que base_len tiene un valor
    // No es constexpr porque base_len no lo es.
    const int MAX_INPUT_LEN = 20 + base_len; // Sin el +1 ya que std::string::length() no incluye el nulo, y memcpy usa esa longitud.

    // buffers host/device
    byte* h_in_data = nullptr;
    word* h_in_lengths = nullptr; // Nuevo buffer para almacenar las longitudes
    byte* h_out = nullptr;

    cudaHostAlloc(&h_in_data, (size_t)BATCH_SIZE * MAX_INPUT_LEN, cudaHostAllocDefault); // Cast a size_t
    cudaHostAlloc(&h_in_lengths, (size_t)BATCH_SIZE * sizeof(word), cudaHostAllocDefault); // Asignar para las longitudes, cast a size_t
    cudaHostAlloc(&h_out, (size_t)BATCH_SIZE * 16, cudaHostAllocDefault); // Cast a size_t

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
            std::string nonce_str = std::to_string(nonce);
            std::string full_input_str = nonce_str + base;

            // Copiar la cadena al buffer de entrada y almacenar su longitud
            std::memcpy(h_in_data + i * MAX_INPUT_LEN, full_input_str.c_str(), full_input_str.length());
            h_in_lengths[i] = (word)full_input_str.length(); // Cast a 'word' (unsigned int)
        }

        // Llamar batch kernel
        mcm_cuda_md5_hash_batch(
            h_in_data,
            h_in_lengths, // Pasar el buffer de longitudes
            (word)MAX_INPUT_LEN, // Pasar la máxima longitud para el cálculo de offset (cast a word)
            h_out,
            (word)this_batch); // Cast a word

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
            hex[2 * i]      = digs[(found_hash[i] >> 4) & 0xF];
            hex[2 * i + 1] = digs[found_hash[i] & 0xF];
        }
        std::printf("Nonce encontrado: %lu\n", found_nonce);
        std::printf("Hash resultante: %s\n", hex);
    } else {
        std::printf("No se encontro un hash que comience con '%s' en el rango [%lu, %lu]\n",
                     prefix, inicio, fin);
    }

    // limpiar
    cudaFreeHost(h_in_data);
    cudaFreeHost(h_in_lengths);
    cudaFreeHost(h_out);

    return EXIT_SUCCESS;
}