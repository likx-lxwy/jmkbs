package com.example.demo.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class AlipaySigner {

    public static String sign(Map<String, String> params, String privateKeyPem) throws Exception {
        Map<String, String> copy = new TreeMap<>(params);
        copy.remove("sign");
        // Alipay page-pay request signing keeps sign_type in the signed payload.
        String content = buildSignContent(copy);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(loadPrivateKey(privateKeyPem));
        signature.update(content.getBytes(StandardCharsets.UTF_8));
        byte[] signed = signature.sign();
        return Base64.getEncoder().encodeToString(signed);
    }

    public static boolean verify(Map<String, String> params, String sign, String publicKeyPem) {
        try {
            Map<String, String> copy = new TreeMap<>(params);
            copy.remove("sign");
            copy.remove("sign_type");
            String content = buildSignContent(copy);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(loadPublicKey(publicKeyPem));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildSignContent(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    private static PrivateKey loadPrivateKey(String pem) throws Exception {
        String cleaned = normalizePemBase64(pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", ""));
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        try {
            // PKCS8
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            // 尝试将 PKCS1 转 PKCS8
            byte[] pkcs8 = convertPkcs1ToPkcs8(decoded);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        }
    }

    private static PublicKey loadPublicKey(String pem) throws Exception {
        String cleaned = normalizePemBase64(pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", ""));
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private static String normalizePemBase64(String pemBody) {
        String cleaned = pemBody.replaceAll("\\s+", "");
        int firstPadding = cleaned.indexOf('=');
        if (firstPadding < 0) {
            return cleaned;
        }
        int end = firstPadding;
        while (end < cleaned.length() && cleaned.charAt(end) == '=') {
            end++;
        }
        return cleaned.substring(0, end);
    }

    private static byte[] convertPkcs1ToPkcs8(byte[] pkcs1) {
        // Minimal wrapper for RSA private key: PKCS8 header for RSA
        // ASN.1: SEQUENCE { version, AlgorithmIdentifier, PrivateKey OCTET STRING }
        try {
            byte[] pkcs8Header = new byte[]{
                    0x30, (byte) 0x82, 0x01, 0x22, // SEQUENCE len
                    0x02, 0x01, 0x00, // version
                    0x30, 0x0d, 0x06, 0x09,
                    0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, // rsaEncryption OID
                    0x05, 0x00, // NULL
                    0x04, (byte) 0x82, 0x01, 0x0e // OCTET STRING len
            };
            byte[] pkcs8 = new byte[pkcs8Header.length + pkcs1.length];
            System.arraycopy(pkcs8Header, 0, pkcs8, 0, pkcs8Header.length);
            System.arraycopy(pkcs1, 0, pkcs8, pkcs8Header.length, pkcs1.length);
            // fix lengths: total length and octet length
            int totalLen = pkcs8.length - 4;
            pkcs8[2] = (byte) ((totalLen >> 8) & 0xff);
            pkcs8[3] = (byte) (totalLen & 0xff);
            int octetLen = pkcs1.length;
            pkcs8[26] = (byte) ((octetLen >> 8) & 0xff);
            pkcs8[27] = (byte) (octetLen & 0xff);
            return pkcs8;
        } catch (Exception e) {
            throw new RuntimeException("私钥格式不支持，请使用PKCS8(RSA2)", e);
        }
    }
}
