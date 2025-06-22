package com.blockchain.miningpool.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatusCode;

import java.io.Serializable;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class RegisterResponse {

    private HttpStatusCode statusCode;
    private String message;

}
