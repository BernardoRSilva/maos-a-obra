package pt.maosaobra;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedPasswords {

    // As contas fictícias usam uma palavra-passe de demonstração.
    // Guarda-se apenas o hash BCrypt na base de dados.
    @Bean(name = "seedPasswordsRunner")
    public ApplicationRunner seedPasswords(
            JdbcTemplate db,
            PasswordEncoder encoder) {

        return args -> {
            db.update(
                "UPDATE volunteers SET password_hash=? WHERE password_hash='SEED_PENDING'",
                encoder.encode("Demo2026!")
            );
        };
    }
}