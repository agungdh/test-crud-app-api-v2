package db.migration;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class V2__seed_admin extends BaseJavaMigration {

    private static final Argon2Function ARGON2 = Argon2Function.getInstance(
            8192, 3, 1, 32, Argon2.ID, 19
    );

    private static String hash(String plain) {
        return Password.hash(plain).with(ARGON2).getResult();
    }

    @Override
    public void migrate(Context context) throws Exception {
        // idempotent: cek dulu apakah admin sudah ada (deleted_at IS NULL)
        try (PreparedStatement ps = context.getConnection().prepareStatement(
                "SELECT id FROM users WHERE username = ? AND deleted_at IS NULL")) {
            ps.setString(1, "admin");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return; // sudah ada, skip
            }
        }

        String hash = hash("admin");

        try (PreparedStatement ps = context.getConnection().prepareStatement(
                "INSERT INTO users (username, password, name) VALUES (?, ?, ?)")) {
            ps.setString(1, "admin");
            ps.setString(2, hash);
            ps.setString(3, "Admin");
            ps.executeUpdate();
        }
    }
}
