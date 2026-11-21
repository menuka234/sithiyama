package lk.sithiyama.backend.config;

import lk.sithiyama.backend.domain.User;
import lk.sithiyama.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The PostgreSQL profile seeds the demo accounts through Flyway. The local
 * MySQL profile disables Flyway, so this small runner keeps the documented
 * accounts available there as well. It only inserts missing users and never
 * changes an existing password.
 *
 * <p>Disable with {@code SITHIYAMA_DEMO_DATA=false}.</p>
 */
@Component
@Profile("mysql")
@ConditionalOnProperty(name = "sithiyama.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class MySqlDemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MySqlDemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public MySqlDemoDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seed("admin", "admin@sithiyama.lk", "SITHIYAMA Admin", null, "en", User.Role.ROLE_ADMIN);
        seed("guide", "guide@sithiyama.lk", "Knuckles Trail Guide", "+94770000001", "si", User.Role.ROLE_GUIDE);
        seed("ranger", "ranger@sithiyama.lk", "Riverston Ranger Post", "+94770000002", "en", User.Role.ROLE_RANGER);
    }

    private void seed(String username,
                      String email,
                      String displayName,
                      String phone,
                      String locale,
                      User.Role role) {
        if (userRepository.existsByUsernameIgnoreCase(username)
                || userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setDisplayName(displayName);
        user.setPhone(phone);
        user.setLocale(locale);
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);

        log.warn("Seeded local MySQL demo account '{}' with the documented demo password. "
                + "Do not use this profile in production.", username);
    }
}
