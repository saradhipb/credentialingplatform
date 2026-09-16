package edu.gcu.capstone.credentialing.repository;

import edu.gcu.capstone.credentialing.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * AppUserRepository.java
 *
 * <p>Data-access interface for platform accounts, used by the authentication
 * service and the administrator's user-management screen.</p>
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
