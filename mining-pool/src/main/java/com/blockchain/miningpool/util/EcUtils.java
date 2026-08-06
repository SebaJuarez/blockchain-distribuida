package com.blockchain.miningpool.util;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

/**
 * Utilidades EC (secp256k1) del pool.
 *
 * Se usa BouncyCastle en vez de SunEC porque necesitamos garantizar la MISMA
 * curva (secp256k1) que usan los mineros Python (cryptography/SECP256K1)
 */
public final class EcUtils {

    public static final String CURVE_NAME = "secp256k1";
    private static final ECNamedCurveParameterSpec CURVE_SPEC = ECNamedCurveTable.getParameterSpec(CURVE_NAME);

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private EcUtils() {
    }

    /**
     * Valida que el hex represente un punto EC válido sobre secp256k1.
     * Comprimido: 33 bytes (66 hex chars), prefijo 0x02/0x03.
     * Descomprimido: 65 bytes (130 hex chars), prefijo 0x04.
     */
    public static boolean isValidPublicKeyHex(String publicKeyHex) {
        if (publicKeyHex == null || publicKeyHex.isBlank()) return false;
        try {
            byte[] raw = Hex.decode(publicKeyHex);
            if (raw.length != 33 && raw.length != 65) return false;
            if (raw[0] != 0x02 && raw[0] != 0x03 && raw[0] != 0x04) return false;
            ECPoint point = CURVE_SPEC.getCurve().decodePoint(raw); // lanza si no está en la curva
            return !point.isInfinity();
        } catch (Exception e) {
            return false;
        }
    }

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC", "BC");
        gen.initialize(new ECGenParameterSpec(CURVE_NAME));
        return gen.generateKeyPair();
    }

    public static String publicKeyToCompressedHex(PublicKey publicKey) {
        ECPublicKey ecPublicKey = (ECPublicKey) publicKey;
        byte[] encoded = ecPublicKey.getQ().getEncoded(true); // true = comprimido
        return Hex.toHexString(encoded);
    }

    public static PublicKey decodePublicKeyHex(String publicKeyHex) throws Exception {
        byte[] raw = Hex.decode(publicKeyHex);
        ECPoint point = CURVE_SPEC.getCurve().decodePoint(raw);
        ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, CURVE_SPEC);
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");
        return kf.generatePublic(pubSpec);
    }
}