package com.ginogipsy.sanmartino.stands;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableJpaAuditing
@EnableCaching
public class StandsConfig {

    public static final String CACHE_STANDS_LIST = "standsList";
    public static final String CACHE_STAND_DETAIL = "standDetail";
    public static final String CACHE_STAND_QUEUE_SNAPSHOTS = "standQueueSnapshots";
    public static final String CACHE_STAND_ANALYTICS = "standAnalytics";
    public static final String CACHE_STAND_REVIEWS = "standReviews";

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CACHE_STANDS_LIST,
                CACHE_STAND_DETAIL,
                CACHE_STAND_QUEUE_SNAPSHOTS,
                CACHE_STAND_ANALYTICS,
                CACHE_STAND_REVIEWS
        );
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(15, TimeUnit.SECONDS)
                        .recordStats()
        );
        return manager;
    }
}
