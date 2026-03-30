package com.dryrun.brogres.repo;

import com.dryrun.brogres.data.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByNickIgnoreCase(String nick);

    boolean existsByNickIgnoreCase(String nick);
}
