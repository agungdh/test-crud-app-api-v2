package id.my.agungdh.common.infrastructure.security;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;

public final class Argon2Hasher {
    private static final Argon2Function ARGON2 = Argon2Function.getInstance(
            8192, 3, 1, 32, Argon2.ID, 19
    );

    private Argon2Hasher() {}

    public static String hash(String plain) {
        return Password.hash(plain).with(ARGON2).getResult();
    }

    public static boolean verify(String plain, String hash) {
        return Password.check(plain, hash).with(ARGON2);
    }
}
