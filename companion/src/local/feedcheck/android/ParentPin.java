package local.feedcheck.android;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class ParentPin {
    static final int LENGTH = 6;
    private static final int ITERATIONS = 120_000;

    static boolean valid(char[] pin) {
        if (pin.length != LENGTH) {
            return false;
        }
        for (char digit : pin) {
            if (digit < '0' || digit > '9') {
                return false;
            }
        }
        return true;
    }

    static String create(char[] pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(salt) + ":" + encoder.encodeToString(derive(pin, salt));
    }

    static boolean matches(char[] pin, String verifier) {
        if (!valid(pin) || verifier == null) {
            return false;
        }
        String[] fields = verifier.split(":", -1);
        if (fields.length != 2) {
            return false;
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(fields[0]);
            byte[] hash = decoder.decode(fields[1]);
            return salt.length == 16 && hash.length == 32 && MessageDigest.isEqual(hash, derive(pin, salt));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static byte[] derive(char[] pin, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("PIN verification unavailable", error);
        } finally {
            spec.clearPassword();
        }
    }
}
