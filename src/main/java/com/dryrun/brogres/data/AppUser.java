package com.dryrun.brogres.data;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Application user. {@code passwordPlain} is intentional for the first prototype iteration only.
 */
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "app_user")
@Data
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String nick;

    /**
     * Plain-text password (prototype only); replace with a password hash before any real deployment.
     */
    @Column(name = "password_plain", nullable = false, length = 512)
    private String passwordPlain;
}
