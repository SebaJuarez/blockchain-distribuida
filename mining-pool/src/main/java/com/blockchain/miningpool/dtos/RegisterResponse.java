package com.blockchain.miningpool.dtos;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatusCode;

@AllArgsConstructor
public class RegisterResponse {

    private HttpStatusCode statusCode;
    private String message;

}
