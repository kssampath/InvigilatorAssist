package com.invigilatorassist.config;

import com.invigilatorassist.domain.AppUser;
import com.invigilatorassist.domain.Institution;
import com.invigilatorassist.domain.Role;
import com.invigilatorassist.repository.AppUserRepository;
import com.invigilatorassist.repository.InstitutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local development and the Kubernetes demo deployment ({@code demo} profile): creates a DEMO institution and an
 * admin login so the app is usable straight away. Real institutions are onboarded with the same two rows, created
 * by a platform operator.
 *
 * <p>Several replicas can start at once on Kubernetes. If another replica seeds first, the unique constraint on
 * {@code institution.code} rejects this one's insert and the seeder simply carries on.
 */
@Component
@Profile({"dev", "demo"})
public class DevDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final InstitutionRepository institutions;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public DevDataSeeder(InstitutionRepository institutions, AppUserRepository users, PasswordEncoder passwords,
                         AppProperties props, TransactionTemplate tx) {
        this.institutions = institutions;
        this.users = users;
        this.passwords = passwords;
        this.props = props;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(status -> seed());
        } catch (DataIntegrityViolationException e) {
            log.info("Demo data was seeded by another instance");
        }
    }

    private void seed() {
        if (institutions.findByCode("DEMO").isPresent()) return;
        Institution demo = institutions.save(new Institution("DEMO", "Demo Institute of Technology"));
        AppUser admin = new AppUser(demo.getId(), "admin@demo.edu",
                passwords.encode(props.dev().adminPassword()), "Exam Cell Admin", "admin@demo.edu", Role.ADMIN);
        admin.setMustChangePassword(false);
        users.save(admin);
        log.info("Dev data seeded: institution DEMO, admin user admin@demo.edu");
    }
}
