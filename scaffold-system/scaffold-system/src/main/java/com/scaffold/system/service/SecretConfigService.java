package com.scaffold.system.service;

import com.scaffold.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 服务端配置加密。主密钥仅由环境提供，数据库仅保存带随机 nonce 的认证密文。 */
@Service
public class SecretConfigService {
    private final byte[] master;
    private final SecureRandom random = new SecureRandom();

    public SecretConfigService(@Value("${BOOKKEEPING_SECRET_KEY:}") String encodedMaster) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encodedMaster); }
        catch (IllegalArgumentException ex) { decoded = new byte[0]; }
        master = decoded.length == 32 ? decoded : new byte[0];
    }

    public boolean ready() { return master.length == 32; }

    public String encrypt(String plaintext) {
        requireMaster();
        if (plaintext == null || !plaintext.matches("[!-~]{1,256}")) {
            throw new BusinessException("API Key 必须为1至256个非空白 ASCII 字符");
        }
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, nonce).doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return "v1:" + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) { throw new BusinessException("密钥加密失败"); }
    }

    public String decrypt(String encrypted) {
        requireMaster();
        try {
            if (encrypted == null || !encrypted.startsWith("v1:")) throw new IllegalArgumentException();
            byte[] payload = Base64.getDecoder().decode(encrypted.substring(3));
            if (payload.length < 29) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(payload, 12))
                    .doFinal(Arrays.copyOfRange(payload, 12, payload.length)), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new BusinessException("密钥无法解密，请检查服务端加密密钥或重新配置 API Key"); }
    }

    private Cipher cipher(int mode, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(master, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD("scaffold-secret-config-v1".getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private void requireMaster() {
        if (!ready()) throw new BusinessException("未配置有效的 BOOKKEEPING_SECRET_KEY（Base64 编码的32字节密钥）");
    }
}
