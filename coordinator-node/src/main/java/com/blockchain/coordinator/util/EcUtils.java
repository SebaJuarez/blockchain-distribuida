package com.blockchain.coordinator.util;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;

/**
 * El coordinador solo necesita reconstruir claves públicas desde hex para
 * verificar firmas ECDSA de transacciones entrantes. No tiene identidad
 * propia: no cobra ni firma nada.
 */
public final class EcUtils {

    private static final String CURVE_NAME = "secp256k1";
    private static final ECNamedCurveParameterSpec CURVE_SPEC = ECNamedCurveTable.getParameterSpec(CURVE_NAME);

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private EcUtils() {
    }

    public static PublicKey decodePublicKeyHex(String publicKeyHex) throws Exception {
        byte[] raw = Hex.decode(publicKeyHex);
        ECPoint point = CURVE_SPEC.getCurve().decodePoint(raw);
        ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, CURVE_SPEC);
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");
        return kf.generatePublic(pubSpec);
    }
}