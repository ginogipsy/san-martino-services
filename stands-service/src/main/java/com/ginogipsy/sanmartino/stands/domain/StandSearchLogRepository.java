package com.ginogipsy.sanmartino.stands.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StandSearchLogRepository extends JpaRepository<StandSearchLogEntity, UUID> {

    @Query("""
            SELECT s.normalizedQuery AS query,
                   COUNT(s) AS searchCount,
                   AVG(s.resultsCount) AS avgResultsCount,
                   MAX(s.createdAt) AS lastSearchedAt
            FROM StandSearchLogEntity s
            GROUP BY s.normalizedQuery
            ORDER BY COUNT(s) DESC, MAX(s.createdAt) DESC
            """)
    List<SearchQueryAggregation> findTopSearchedQueries(Pageable pageable);

    @Query("""
            SELECT s.normalizedQuery AS query,
                   COUNT(s) AS searchCount,
                   0.0 AS avgResultsCount,
                   MAX(s.createdAt) AS lastSearchedAt
            FROM StandSearchLogEntity s
            WHERE s.resultsCount = 0
            GROUP BY s.normalizedQuery
            ORDER BY COUNT(s) DESC, MAX(s.createdAt) DESC
            """)
    List<SearchQueryAggregation> findTopZeroResultQueries(Pageable pageable);

    interface SearchQueryAggregation {
        String getQuery();

        long getSearchCount();

        double getAvgResultsCount();

        Instant getLastSearchedAt();
    }
}
