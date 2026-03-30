package com.dryrun.brogres.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "brogres.security")
public class BrogresSecurityProperties {

    /**
     * HMAC key for JWT signing; override with env {@code JWT_SECRET} in any shared environment.
     */
    private String jwtSecret = "brogres-dev-secret-key-min-32-chars-long!!";

    /** Access-token lifetime in milliseconds (default 7 days). */
    private long jwtExpirationMs = 604800000L;
}
