/*
 * md5.cuh CUDA Implementation of MD5 digest       
 *
 * Date: 12 June 2019
 * Revision: 1
 * 
 * Based on the public domain Reference Implementation in C, by
 * Brad Conte, original code here:
 *
 * https://github.com/B-Con/crypto-algorithms
 *
 * This file is released into the Public Domain.
 */


#pragma once
#include <stdlib.h> // Para size_t

// Asegúrate de que byte y word estén definidos si no están ya en otro lugar
using byte = unsigned char;
using word = unsigned int; // ¡CAMBIO IMPORTANTE AQUÍ! Debe ser unsigned int para compatibilidad con el kernel y MD5.

extern "C"
{
void mcm_cuda_md5_hash_batch(byte* in_data, word* in_lengths, word max_in_len_per_item, byte* out, word n_batch);
}