package com.blockchain.miningpool.config;

import com.blockchain.miningpool.util.EcUtils;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Identidad EC (secp256k1) del pool. El coordinador usa la clave pública
 * expuesta acá como "receiver" cuando el pool gana un bloque
 * (ver MiningResultServiceImpl).
 */
@Component
public class PoolKeyConfig {

    private static final Logger logger = LoggerFactory.getLogger(PoolKeyConfig.class);

    @Value("${pool.identity.key-file:pool-identity.key}")
    private String keyFilePath;

    @Value("${pool.identity.require-existing:false}")
    private boolean requireExisting;

    @Getter
    private PublicKey publicKey;
    private PrivateKey privateKey;
    @Getter
    private String publicKeyHex;

    @PostConstruct
    public void init() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        File privFile = new File(keyFilePath);
        File pubFile = new File(keyFilePath + ".pub");

        if (privFile.exists() && pubFile.exists()) {
            byte[] privBytes = Files.readAllBytes(privFile.toPath());
            byte[] pubBytes = Files.readAllBytes(pubFile.toPath());
            KeyFactory kf = KeyFactory.getInstance("EC", "BC");
            this.privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            this.publicKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            logger.info("PoolKeyConfig: identidad EC cargada desde {}", keyFilePath);
        } else if (requireExisting) {
            throw new IllegalStateException("PoolKeyConfig: identidad EC no encontrada en " + keyFilePath +
                    " y pool.identity.require-existing=true. No se genera una identidad nueva para evitar " +
                    "dividir el fondo del pool entre claves efímeras.");
        } else {
            KeyPair keyPair = EcUtils.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();
            Files.write(privFile.toPath(), privateKey.getEncoded());
            Files.write(pubFile.toPath(), publicKey.getEncoded());
            logger.info("PoolKeyConfig: nueva identidad EC generada y guardada en {} / {}.pub", keyFilePath, keyFilePath);
        }

        this.publicKeyHex = EcUtils.publicKeyToCompressedHex(this.publicKey);
        logger.info("PoolKeyConfig: clave pública del pool (dirección de recompensas): {}", publicKeyHex);
    }
}