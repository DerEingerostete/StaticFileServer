/*
 * Copyright (c) 2023 - DerEingerostete
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

package de.dereingerostete.sfs.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import de.dereingerostete.sfs.error.RestError;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

public class RateLimiter {
    private static final @NotNull Duration EXPIRE_DURATION = Duration.ofMinutes(10);
    private static final RateLimiter INSTANCE = new RateLimiter();
    private static final int MAX_REQUESTS = 1000;
    protected final Cache<String, Integer> cache = Caffeine.newBuilder()
            .expireAfterWrite(EXPIRE_DURATION).build();

    /**
     * Check if a remote access can access the APIs
     * @param address The remote address to check
     * @return Whether the address can access the APIs
     */
    public boolean cannotAccess(@NotNull String address) {
        Integer accesses = cache.getIfPresent(address);
        if (accesses == null) {
            accesses = 0;
            cache.put(address, accesses);
            return false;
        }

        accesses++;
        if (accesses >= MAX_REQUESTS) return true;
        cache.put(address, accesses);
        return false;
    }

    /**
     * Gets the time left the remote address needs to wait to get access back
     * @param address The remote address to check
     * @return The time left or -1 if the address isn't blocked
     */
    public long getTimeLeft(@NotNull String address) {
        Optional<Policy.FixedExpiration<String, Integer>> optional = cache.policy().expireAfterWrite();
        Policy.FixedExpiration<String, Integer> policy = optional.orElse(null);
        if (policy == null) return -1;

        OptionalLong optionalLong = policy.ageOf(address, TimeUnit.SECONDS);
        if (optionalLong.isEmpty()) return -1;
        else return EXPIRE_DURATION.toSeconds() - optionalLong.getAsLong();
    }

    @NotNull
    public static RateLimiter get() {
        return INSTANCE;
    }

    @NotNull
	public ResponseEntity<Object> createResponse(long timeLeft) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Retry-After", String.valueOf(timeLeft))
                .body(RestError.tooManyRequests(timeLeft));
    }

}