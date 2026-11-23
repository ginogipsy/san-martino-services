package com.ginogipsy.sanmartino.stands.service;

import com.ginogipsy.sanmartino.observability.audit.AuditUserResolver;
import com.ginogipsy.sanmartino.observability.audit.AuditUserResolver.AuditUser;
import com.ginogipsy.sanmartino.observability.audit.Auditable;
import com.ginogipsy.sanmartino.stands.StandsConfig;
import com.ginogipsy.sanmartino.stands.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StandService {

    private final StandRepository repository;
    private final StandSearchLogRepository searchLogRepository;
    private final StandInteractionEventRepository interactionEventRepository;
    private final FestivalReviewRepository reviewRepository;
    private final UserFavoriteStandRepository favoriteStandRepository;
    private final StandOrderRepository orderRepository;
    private final AuditUserResolver auditUserResolver;

    public StandService(StandRepository repository) {
        this(repository, null, null, null, null, null, null);
    }

    public StandService(
            StandRepository repository,
            StandSearchLogRepository searchLogRepository,
            StandInteractionEventRepository interactionEventRepository
    ) {
        this(repository, searchLogRepository, interactionEventRepository, null, null, null, null);
    }

    public StandService(
            StandRepository repository,
            StandSearchLogRepository searchLogRepository,
            StandInteractionEventRepository interactionEventRepository,
            FestivalReviewRepository reviewRepository,
            AuditUserResolver auditUserResolver
    ) {
        this(repository, searchLogRepository, interactionEventRepository, reviewRepository, null, null, auditUserResolver);
    }

    public StandService(
            StandRepository repository,
            StandSearchLogRepository searchLogRepository,
            StandInteractionEventRepository interactionEventRepository,
            FestivalReviewRepository reviewRepository,
            UserFavoriteStandRepository favoriteStandRepository,
            AuditUserResolver auditUserResolver
    ) {
        this(repository, searchLogRepository, interactionEventRepository, reviewRepository, favoriteStandRepository, null, auditUserResolver);
    }

    @Autowired
    public StandService(
            StandRepository repository,
            StandSearchLogRepository searchLogRepository,
            StandInteractionEventRepository interactionEventRepository,
            FestivalReviewRepository reviewRepository,
            UserFavoriteStandRepository favoriteStandRepository,
            StandOrderRepository orderRepository,
            AuditUserResolver auditUserResolver
    ) {
        this.repository = repository;
        this.searchLogRepository = searchLogRepository;
        this.interactionEventRepository = interactionEventRepository;
        this.reviewRepository = reviewRepository;
        this.favoriteStandRepository = favoriteStandRepository;
        this.orderRepository = orderRepository;
        this.auditUserResolver = auditUserResolver;
    }

    /**
     * Calcola una penalità (-20..0 punti) in base alla lunghezza della coda ordini attiva
     * sulla pulsantiera della cantina (Step 6 del motore di ricerca), premiando a parità di piatto
     * le cantine con tempi di attesa più brevi.
     */
    static int computeWaitTimePenalty(int activeOrdersCount) {
        if (activeOrdersCount <= 2) {
            return 0;
        }
        return -Math.min(20, (activeOrdersCount - 2) * 3);
    }

    @Transactional(readOnly = true)
    @Cacheable(StandsConfig.CACHE_STANDS_LIST)
    public List<StandEntity> findAll() {
        List<StandEntity> stands = repository.findAllByOrderByNumberAsc();
        for (StandEntity stand : stands) {
            stand.getOwners().size();
            stand.getMenuItems().forEach(item -> item.getKeywords().size());
        }
        return stands;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = StandsConfig.CACHE_STAND_DETAIL, key = "#id")
    public StandEntity findById(UUID id) {
        StandEntity stand = repository.findById(id).orElseThrow(() -> new StandNotFoundException(id));
        // Materialize lazy collections so the mapper can read them outside the session
        stand.getOwners().size();
        stand.getMenuItems().forEach(item -> item.getKeywords().size());
        return stand;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_ANALYTICS, allEntries = true)
    })
    @Auditable(
            action = "STAND:CREATE",
            description = "'Creazione cantina: ' + #stand.name + ' (#' + #stand.number + ')'",
            resourceId = "#result?.id?.toString()"
    )
    public StandEntity create(StandEntity stand) {
        stand.setId(UUID.randomUUID());
        return repository.save(stand);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_ANALYTICS, allEntries = true)
    })
    @Auditable(
            action = "STAND:UPDATE",
            description = "'Modifica cantina: ' + #update.name + ' (#' + #update.number + ')'",
            resourceId = "#id?.toString()"
    )
    public StandEntity update(UUID id, StandEntity update) {
        StandEntity current = findById(id);
        current.setNumber(update.getNumber());
        current.setName(update.getName());
        current.setDescriptionIt(update.getDescriptionIt());
        current.setDescriptionEn(update.getDescriptionEn());
        current.setFirstParticipationYear(update.getFirstParticipationYear());
        current.setLatitude(update.getLatitude());
        current.setLongitude(update.getLongitude());
        return current;
    }

    @Transactional(readOnly = true)
    public List<MenuItemEntity> listMenuItems(UUID standId, MenuKind kindFilter) {
        StandEntity stand = findById(standId);
        List<MenuItemEntity> items = stand.getMenuItems();
        if (kindFilter == null) return items;
        return items.stream().filter(it -> it.getKind() == kindFilter).toList();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_ANALYTICS, allEntries = true)
    })
    @Auditable(
            action = "STAND:DELETE",
            description = "'Eliminazione cantina: ' + #id",
            resourceId = "#id?.toString()"
    )
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new StandNotFoundException(id);
        }
        repository.deleteById(id);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_ANALYTICS, allEntries = true)
    })
    @Auditable(
            action = "STAND:MENU_ITEM_ADD",
            description = "'Aggiunta piatto al menu: ' + #item.name + ' per cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public MenuItemEntity addMenuItem(UUID standId, MenuItemEntity item) {
        StandEntity stand = findById(standId);
        item.setId(UUID.randomUUID());
        item.setKeywords(enrichKeywordsWithAutoSuggestions(item.getName(), item.getDescriptionIt(), item.getKeywords()));
        stand.addMenuItem(item);
        return item;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_ANALYTICS, allEntries = true)
    })
    @Auditable(
            action = "STAND:MENU_ITEM_UPDATE",
            description = "'Modifica piatto menu: ' + #update.name + ' (' + #itemId + ') per cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public MenuItemEntity updateMenuItem(UUID standId, UUID itemId, MenuItemEntity update) {
        StandEntity stand = findById(standId);
        MenuItemEntity current = stand.getMenuItems().stream()
                .filter(it -> it.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new MenuItemNotFoundException(standId, itemId));
        current.setName(update.getName());
        current.setDescriptionIt(update.getDescriptionIt());
        current.setDescriptionEn(update.getDescriptionEn());
        current.setAvailablePlates(update.getAvailablePlates());
        current.setKind(update.getKind());
        current.setKeywords(enrichKeywordsWithAutoSuggestions(update.getName(), update.getDescriptionIt(), update.getKeywords()));
        return current;
    }

    @Transactional(readOnly = true)
    public List<StandOwnerEntity> listOwners(UUID standId) {
        return findById(standId).getOwners();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_ANALYTICS, allEntries = true)
    })
    @Auditable(
            action = "STAND:MENU_ITEM_DELETE",
            description = "'Eliminazione piatto menu: ' + #itemId + ' per cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public void deleteMenuItem(UUID standId, UUID itemId) {
        StandEntity stand = findById(standId);
        MenuItemEntity item = stand.getMenuItems().stream()
                .filter(it -> it.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new MenuItemNotFoundException(standId, itemId));
        stand.removeMenuItem(item);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true)
    })
    public StandOwnerEntity addOwner(UUID standId, StandOwnerEntity owner) {
        StandEntity stand = findById(standId);
        owner.setId(UUID.randomUUID());
        stand.addOwner(owner);
        return owner;
    }

    public record StandSearchHit(StandEntity stand, List<MenuItemEntity> matchedItems, int score) {}

    /**
     * Calcola un bonus di popolarità (0..25 punti) basato sui click ricevuti dal piatto,
     * utile per ordinare a parità di rilevanza testuale i piatti più richiesti della festa.
     */
    static int computePopularityBonus(long clickCount) {
        if (clickCount <= 0) {
            return 0;
        }
        int logBonus = (int) Math.round(Math.log1p(clickCount) * 6.0);
        return Math.min(25, Math.max(2, logBonus));
    }

    /**
     * Calcola un bonus di qualità (-10..+20 punti) basato sulla media dei voti approvati (1..5 stelle)
     * del piatto (TODO Step 4 del motore di ricerca).
     */
    static int computeRatingBonus(Double avgRating) {
        if (avgRating == null || avgRating <= 0.0) {
            return 0;
        }
        return (int) Math.round((avgRating - 3.0) * 10.0);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true)
    })
    public void deleteOwner(UUID standId, UUID ownerId) {
        StandEntity stand = findById(standId);
        StandOwnerEntity owner = stand.getOwners().stream()
                .filter(o -> o.getId().equals(ownerId))
                .findFirst()
                .orElseThrow(() -> new OwnerNotFoundException(standId, ownerId));
        stand.removeOwner(owner);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    @Transactional
    public List<StandSearchHit> searchStands(String query, MenuKind kindFilter, boolean onlyAvailable) {
        return searchStands(query, kindFilter, onlyAvailable, null, null);
    }

    /**
     * Ricerca intelligente cross-cantina su piatti, parole chiave (`keywords`),
     * descrizioni IT/EN e anagrafica cantina, con:
     * - normalizzazione accenti e rimozione stopword
     * - espansione sinonimi gastronomici (`CULINARY_SYNONYMS`)
     * - tolleranza agli errori di battitura (distanza di Damerau-Levenshtein <= 1 o 2)
     * - boost di disponibilità (`availablePlates > 0`), popolarità (click storici sul piatto),
     *   rating medio approvato (`festival_reviews`), vicinanza GPS opzionale e coda attiva pulsantiera.
     * - salvataggio automatico della ricerca in `stand_search_logs` se la query normalizzata ha lunghezza >= 2.
     */
    @Transactional
    public List<StandSearchHit> searchStands(
            String query,
            MenuKind kindFilter,
            boolean onlyAvailable,
            Double userLat,
            Double userLng
    ) {
        List<StandEntity> allStands = repository.findAllByOrderByNumberAsc();
        List<String> tokens = tokenizeQuery(query);
        String fullNormalizedQuery = normalizeText(query);

        Map<UUID, Long> itemClickCounts = loadMenuItemClickCounts();
        Map<UUID, Double> itemApprovedRatings = loadApprovedMenuItemRatings();
        Map<UUID, Integer> standActiveOrderCounts = loadActiveOrderCountsByStand();

        List<StandSearchHit> results = new java.util.ArrayList<>();

        for (StandEntity stand : allStands) {
            List<MenuItemEntity> menuItems = stand.getMenuItems();
            menuItems.forEach(item -> item.getKeywords().size());
            int activeQueueCount = stand.getId() != null ? standActiveOrderCounts.getOrDefault(stand.getId(), 0) : 0;
            int waitTimePenalty = computeWaitTimePenalty(activeQueueCount);

            if (tokens.isEmpty()) {
                List<MenuItemEntity> filteredItems = menuItems.stream()
                        .filter(item -> matchesFilters(item, kindFilter, onlyAvailable))
                        .toList();
                if ((kindFilter != null || onlyAvailable) && filteredItems.isEmpty()) {
                    continue;
                }
                int gpsScore = computeProximityBonus(stand, userLat, userLng) + waitTimePenalty;
                results.add(new StandSearchHit(stand, filteredItems, gpsScore));
                continue;
            }

            int standLevelScore = scoreStandMetadata(stand, fullNormalizedQuery, tokens);

            record ScoredItem(MenuItemEntity item, int score) {}
            List<ScoredItem> matchedScoredItems = new java.util.ArrayList<>();

            for (MenuItemEntity item : menuItems) {
                if (!matchesFilters(item, kindFilter, onlyAvailable)) {
                    continue;
                }
                int itemScore = scoreMenuItem(item, fullNormalizedQuery, tokens);
                if (itemScore > 0) {
                    long clicks = item.getId() != null ? itemClickCounts.getOrDefault(item.getId(), 0L) : 0L;
                    int popularityBonus = computePopularityBonus(clicks);
                    int ratingBonus = item.getId() != null
                            ? computeRatingBonus(itemApprovedRatings.get(item.getId()))
                            : 0;
                    matchedScoredItems.add(new ScoredItem(item, Math.max(1, itemScore + popularityBonus + ratingBonus)));
                }
            }

            matchedScoredItems.sort(java.util.Comparator.comparingInt(ScoredItem::score).reversed());

            if (!matchedScoredItems.isEmpty() || (standLevelScore > 0 && kindFilter == null && !onlyAvailable)) {
                int proximityBonus = computeProximityBonus(stand, userLat, userLng);
                int totalScore = Math.max(
                        1,
                        standLevelScore
                                + matchedScoredItems.stream().mapToInt(ScoredItem::score).sum()
                                + proximityBonus
                                + waitTimePenalty
                );
                List<MenuItemEntity> matchedItems = matchedScoredItems.stream()
                        .map(ScoredItem::item)
                        .toList();
                results.add(new StandSearchHit(stand, matchedItems, totalScore));
            }
        }

        if (!tokens.isEmpty() || (userLat != null && userLng != null)) {
            results.sort(
                    java.util.Comparator.comparingInt(StandSearchHit::score).reversed()
                            .thenComparingInt(hit -> hit.stand().getNumber())
            );
        }

        recordSearchIfEligible(query, fullNormalizedQuery, kindFilter, onlyAvailable, results.size());

        return results;
    }

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "il", "lo", "la", "i", "gli", "le", "un", "uno", "una",
            "di", "a", "da", "in", "con", "su", "per", "tra", "fra",
            "al", "allo", "alla", "ai", "agli", "alle",
            "del", "dello", "della", "dei", "degli", "delle",
            "nel", "nella", "sul", "sulla", "col", "e", "o", "the", "with", "of", "and",
            "piatto", "porzione", "porzioni", "caldo", "calda", "caldi", "calde",
            "fresco", "fresca", "servito", "servita", "servite", "fatto", "fatta", "fatte"
    );

    /**
     * Dizionario sinonimi e categorie gastronomiche tipiche della festa di San Martino:
     * espande la ricerca dell'utente e arricchisce automaticamente le keyword dei piatti.
     */
    private static final java.util.Map<String, java.util.Set<String>> CULINARY_SYNONYMS = java.util.Map.ofEntries(
            java.util.Map.entry("pasta", java.util.Set.of("primo", "bucatini", "fettuccine", "rigatoni", "spaghetti", "penne", "maccheroni", "sagnozzi", "tonnarelli", "strozzapreti")),
            java.util.Map.entry("primo", java.util.Set.of("pasta", "gnocchi", "polenta", "zuppa", "minestra", "fettuccine", "bucatini", "sagnozzi", "risotto")),
            java.util.Map.entry("amatriciana", java.util.Set.of("pasta", "primo", "sugo", "guanciale", "pecorino", "bucatini", "pomodoro")),
            java.util.Map.entry("carbonara", java.util.Set.of("pasta", "primo", "guanciale", "pecorino", "uovo")),
            java.util.Map.entry("cacio", java.util.Set.of("pasta", "primo", "pepe", "pecorino", "formaggio")),
            java.util.Map.entry("gnocchi", java.util.Set.of("primo", "patate", "pasta")),
            java.util.Map.entry("polenta", java.util.Set.of("primo", "mais", "salsiccia", "spuntature", "sugo")),
            java.util.Map.entry("carne", java.util.Set.of("secondo", "salsiccia", "arrosticini", "braciola", "maiale", "pecora", "abbacchio", "spezzatino", "trippa", "guanciale", "pancetta", "porchetta")),
            java.util.Map.entry("secondo", java.util.Set.of("carne", "salsiccia", "arrosticini", "porchetta", "trippa", "spezzatino")),
            java.util.Map.entry("salsiccia", java.util.Set.of("carne", "secondo", "maiale", "brace")),
            java.util.Map.entry("arrosticini", java.util.Set.of("carne", "secondo", "pecora", "brace", "spiedini")),
            java.util.Map.entry("panino", java.util.Set.of("pane", "panini", "bruschetta", "bruschette", "pizzella", "pizzelle", "fritto")),
            java.util.Map.entry("pizzelle", java.util.Set.of("pizzella", "fritto", "pasta fritta")),
            java.util.Map.entry("castagne", java.util.Set.of("castagna", "caldarroste", "lesse", "arrostite", "brace", "autunno")),
            java.util.Map.entry("caldarroste", java.util.Set.of("castagne", "castagna", "arrostite", "brace")),
            java.util.Map.entry("tartufo", java.util.Set.of("tartufi", "nero", "bianco")),
            java.util.Map.entry("vino", java.util.Set.of("rosso", "bianco", "novello", "calice", "bottiglia", "cesanese", "cabernet", "merlot", "passerina", "malvasia")),
            java.util.Map.entry("birra", java.util.Set.of("artigianale", "bionda", "rossa", "ipa", "spina")),
            java.util.Map.entry("dolce", java.util.Set.of("dolci", "dessert", "ciambella", "crostata", "torta", "biscotti", "nutella", "cioccolato"))
    );

    @Transactional
    @CacheEvict(value = StandsConfig.CACHE_STAND_ANALYTICS, allEntries = true)
    public void recordInteraction(
            UUID standId,
            UUID menuItemId,
            StandInteractionType interactionType,
            String searchQuery
    ) {
        StandEntity stand = repository.findById(standId)
                .orElseThrow(() -> new StandNotFoundException(standId));

        if (menuItemId != null) {
            boolean belongsToStand = stand.getMenuItems().stream()
                    .anyMatch(item -> menuItemId.equals(item.getId()));
            if (!belongsToStand) {
                throw new MenuItemNotFoundException(standId, menuItemId);
            }
        }

        if (interactionEventRepository != null) {
            String normalizedSearch = searchQuery == null || searchQuery.isBlank()
                    ? null
                    : truncate(normalizeText(searchQuery), 200);
            StandInteractionEventEntity event = new StandInteractionEventEntity(
                    UUID.randomUUID(),
                    standId,
                    menuItemId,
                    interactionType,
                    normalizedSearch,
                    Instant.now()
            );
            interactionEventRepository.save(event);
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = StandsConfig.CACHE_STAND_ANALYTICS, key = "#limit")
    public AnalyticsSummaryView getAnalyticsSummary(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        PageRequest page = PageRequest.of(0, safeLimit);

        long totalSearches = searchLogRepository != null ? searchLogRepository.count() : 0L;
        long totalInteractions = interactionEventRepository != null ? interactionEventRepository.count() : 0L;

        List<SearchQueryStatView> topSearches = searchLogRepository == null
                ? List.of()
                : searchLogRepository.findTopSearchedQueries(page).stream()
                .map(agg -> new SearchQueryStatView(
                        agg.getQuery(),
                        agg.getSearchCount(),
                        Math.round(agg.getAvgResultsCount() * 10.0) / 10.0,
                        agg.getLastSearchedAt()
                ))
                .toList();

        List<SearchQueryStatView> zeroResultQueries = searchLogRepository == null
                ? List.of()
                : searchLogRepository.findTopZeroResultQueries(page).stream()
                .map(agg -> new SearchQueryStatView(
                        agg.getQuery(),
                        agg.getSearchCount(),
                        0.0,
                        agg.getLastSearchedAt()
                ))
                .toList();

        List<StandEntity> allStands = repository.findAllByOrderByNumberAsc();
        Map<UUID, StandEntity> standsById = allStands.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(StandEntity::getId, s -> s, (a, b) -> a));

        Map<UUID, MenuItemEntity> itemsById = new java.util.HashMap<>();
        for (StandEntity stand : allStands) {
            for (MenuItemEntity item : stand.getMenuItems()) {
                if (item.getId() != null) {
                    itemsById.put(item.getId(), item);
                }
            }
        }

        List<MenuItemClickStatView> topClickedMenuItems = interactionEventRepository == null
                ? List.of()
                : interactionEventRepository.findTopClickedMenuItems(page).stream()
                .map(agg -> {
                    MenuItemEntity item = itemsById.get(agg.getMenuItemId());
                    if (item == null || item.getStand() == null) return null;
                    StandEntity stand = item.getStand();
                    return new MenuItemClickStatView(
                            item.getId(),
                            item.getName(),
                            item.getKind(),
                            stand.getId(),
                            stand.getNumber(),
                            stand.getName(),
                            agg.getClickCount()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        List<StandClickStatView> topClickedStands = interactionEventRepository == null
                ? List.of()
                : interactionEventRepository.findTopClickedStands(page).stream()
                .map(agg -> {
                    StandEntity stand = standsById.get(agg.getStandId());
                    if (stand == null) return null;
                    return new StandClickStatView(
                            stand.getId(),
                            stand.getNumber(),
                            stand.getName(),
                            agg.getInteractionCount()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return new AnalyticsSummaryView(
                totalSearches,
                totalInteractions,
                topSearches,
                zeroResultQueries,
                topClickedMenuItems,
                topClickedStands
        );
    }

    private void recordSearchIfEligible(
            String rawQuery,
            String normalizedQuery,
            MenuKind kindFilter,
            boolean onlyAvailable,
            int resultsCount
    ) {
        if (searchLogRepository == null || normalizedQuery == null || normalizedQuery.length() < 2) {
            return;
        }
        String cleanRaw = truncate(rawQuery != null ? rawQuery.trim() : normalizedQuery, 200);
        String cleanNorm = truncate(normalizedQuery, 200);
        StandSearchLogEntity logEntity = new StandSearchLogEntity(
                UUID.randomUUID(),
                cleanRaw,
                cleanNorm,
                kindFilter,
                onlyAvailable,
                resultsCount,
                Instant.now()
        );
        searchLogRepository.save(logEntity);
    }

    private Map<UUID, Long> loadMenuItemClickCounts() {
        if (interactionEventRepository == null) {
            return Map.of();
        }
        return interactionEventRepository.findAllMenuItemClickCounts().stream()
                .filter(agg -> agg.getMenuItemId() != null)
                .collect(Collectors.toMap(
                        StandInteractionEventRepository.MenuItemClickAggregation::getMenuItemId,
                        StandInteractionEventRepository.MenuItemClickAggregation::getClickCount,
                        Long::sum
                ));
    }

    private Map<UUID, Double> loadApprovedMenuItemRatings() {
        if (reviewRepository == null) {
            return Map.of();
        }
        return reviewRepository.findApprovedMenuItemRatings().stream()
                .filter(agg -> agg.getTargetId() != null)
                .collect(Collectors.toMap(
                        FestivalReviewRepository.TargetRatingAggregation::getTargetId,
                        FestivalReviewRepository.TargetRatingAggregation::getAvgRating,
                        (a, b) -> a
                ));
    }

    private Map<UUID, Integer> loadActiveOrderCountsByStand() {
        if (orderRepository == null) {
            return Map.of();
        }
        return orderRepository.findAllByStatusIn(List.of(StandOrderStatus.IN_PROGRESS, StandOrderStatus.READY))
                .stream()
                .filter(o -> o.getStandId() != null)
                .collect(Collectors.groupingBy(
                        StandOrderEntity::getStandId,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = StandsConfig.CACHE_STAND_REVIEWS)
    public List<FestivalReviewEntity> listReviews(
            ReviewTargetType targetType,
            UUID targetId,
            ReviewStatus status
    ) {
        if (reviewRepository == null) {
            return List.of();
        }
        return reviewRepository.findFiltered(targetType, targetId, status);
    }

    @Transactional
    @CacheEvict(value = StandsConfig.CACHE_STAND_REVIEWS, allEntries = true)
    @Auditable(
            action = "STAND:REVIEW_CREATE",
            description = "'Invio recensione (' + #targetType + ') voto ' + #rating + '/5 su ' + #targetId",
            resourceId = "#result?.id?.toString()"
    )
    public FestivalReviewEntity createReview(
            ReviewTargetType targetType,
            UUID targetId,
            UUID standId,
            String explicitTargetName,
            String explicitAuthorUsername,
            int rating,
            String comment
    ) {
        AuditUser currentUser = auditUserResolver != null ? auditUserResolver.resolveUser() : AuditUser.ANONYMOUS;
        String authorUserId = currentUser != null && currentUser.userId() != null
                ? currentUser.userId()
                : "anonymous";
        String authorUsername = (currentUser != null
                && currentUser.username() != null
                && !"anonymous".equalsIgnoreCase(currentUser.username()))
                ? currentUser.username()
                : (explicitAuthorUsername != null && !explicitAuthorUsername.isBlank()
                ? explicitAuthorUsername.trim()
                : "Visitatore");

        UUID resolvedStandId = standId;
        String resolvedTargetName = explicitTargetName != null && !explicitTargetName.isBlank()
                ? explicitTargetName.trim()
                : null;

        if (targetType == ReviewTargetType.STAND) {
            StandEntity stand = findById(targetId);
            resolvedStandId = stand.getId();
            resolvedTargetName = "#" + stand.getNumber() + " — " + stand.getName();
        } else if (targetType == ReviewTargetType.MENU_ITEM) {
            if (resolvedStandId != null) {
                final UUID standIdToLookup = resolvedStandId;
                StandEntity stand = findById(standIdToLookup);
                MenuItemEntity item = stand.getMenuItems().stream()
                        .filter(it -> it.getId().equals(targetId))
                        .findFirst()
                        .orElseThrow(() -> new MenuItemNotFoundException(standIdToLookup, targetId));
                resolvedTargetName = item.getName() + " (#" + stand.getNumber() + " " + stand.getName() + ")";
            } else if (resolvedTargetName == null) {
                for (StandEntity stand : repository.findAllByOrderByNumberAsc()) {
                    for (MenuItemEntity item : stand.getMenuItems()) {
                        if (targetId.equals(item.getId())) {
                            resolvedStandId = stand.getId();
                            resolvedTargetName = item.getName() + " (#" + stand.getNumber() + " " + stand.getName() + ")";
                            break;
                        }
                    }
                    if (resolvedTargetName != null) break;
                }
            }
        }

        if (resolvedTargetName == null || resolvedTargetName.isBlank()) {
            resolvedTargetName = targetType.name() + " " + targetId;
        }

        int safeRating = Math.max(1, Math.min(5, rating));
        FestivalReviewEntity review = new FestivalReviewEntity(
                UUID.randomUUID(),
                targetType,
                targetId,
                resolvedStandId,
                truncate(resolvedTargetName, 200),
                truncate(authorUserId, 100),
                truncate(authorUsername, 120),
                safeRating,
                comment.trim(),
                ReviewStatus.PENDING,
                Instant.now()
        );
        return reviewRepository != null ? reviewRepository.save(review) : review;
    }

    @Transactional
    @CacheEvict(value = StandsConfig.CACHE_STAND_REVIEWS, allEntries = true)
    @Auditable(
            action = "STAND:REVIEW_MODERATE",
            description = "'Moderazione recensione ' + #reviewId + ' -> ' + #newStatus",
            resourceId = "#reviewId?.toString()"
    )
    public FestivalReviewEntity moderateReview(
            UUID reviewId,
            ReviewStatus newStatus,
            String moderationNote
    ) {
        if (reviewRepository == null) {
            throw new ReviewNotFoundException(reviewId);
        }
        FestivalReviewEntity review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        AuditUser moderator = auditUserResolver != null ? auditUserResolver.resolveUser() : AuditUser.ANONYMOUS;
        String moderatorName = moderator != null && moderator.username() != null
                ? moderator.username()
                : "moderator";

        review.setStatus(newStatus);
        review.setModeratedBy(truncate(moderatorName, 120));
        review.setModerationNote(moderationNote != null && !moderationNote.isBlank() ? moderationNote.trim() : null);
        Instant now = Instant.now();
        review.setModeratedAt(now);
        review.setUpdatedAt(now);
        return reviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public List<UUID> listFavoriteStandIds() {
        if (favoriteStandRepository == null) {
            return List.of();
        }
        AuditUser currentUser = auditUserResolver != null ? auditUserResolver.resolveUser() : AuditUser.ANONYMOUS;
        if (currentUser == null || currentUser.userId() == null || "anonymous".equalsIgnoreCase(currentUser.userId())) {
            return List.of();
        }
        return favoriteStandRepository.findAllByUserIdOrderByCreatedAtDesc(currentUser.userId())
                .stream()
                .map(UserFavoriteStandEntity::getStandId)
                .toList();
    }

    @Transactional
    @Auditable(
            action = "STAND:FAVORITE_ADD",
            description = "'Aggiunta cantina preferita: ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public void addFavoriteStand(UUID standId) {
        if (favoriteStandRepository == null) {
            return;
        }
        if (!repository.existsById(standId)) {
            throw new StandNotFoundException(standId);
        }
        AuditUser currentUser = auditUserResolver != null ? auditUserResolver.resolveUser() : AuditUser.ANONYMOUS;
        if (currentUser == null || currentUser.userId() == null || "anonymous".equalsIgnoreCase(currentUser.userId())) {
            return;
        }
        String userId = truncate(currentUser.userId(), 120);
        String username = truncate(
                currentUser.username() != null && !currentUser.username().isBlank()
                        ? currentUser.username()
                        : userId,
                120
        );
        if (favoriteStandRepository.findByUserIdAndStandId(userId, standId).isEmpty()) {
            UserFavoriteStandEntity entity = new UserFavoriteStandEntity(
                    UUID.randomUUID(),
                    userId,
                    username,
                    standId,
                    Instant.now()
            );
            favoriteStandRepository.save(entity);
        }
    }

    @Transactional
    @Auditable(
            action = "STAND:FAVORITE_REMOVE",
            description = "'Rimozione cantina preferita: ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public void removeFavoriteStand(UUID standId) {
        if (favoriteStandRepository == null) {
            return;
        }
        AuditUser currentUser = auditUserResolver != null ? auditUserResolver.resolveUser() : AuditUser.ANONYMOUS;
        if (currentUser == null || currentUser.userId() == null || "anonymous".equalsIgnoreCase(currentUser.userId())) {
            return;
        }
        favoriteStandRepository.deleteByUserIdAndStandId(truncate(currentUser.userId(), 120), standId);
    }

    public record SearchQueryStatView(String query, long searchCount, double avgResultsCount, Instant lastSearchedAt) {
    }

    public record MenuItemClickStatView(
            UUID menuItemId,
            String menuItemName,
            MenuKind kind,
            UUID standId,
            int standNumber,
            String standName,
            long clickCount
    ) {
    }

    public record StandClickStatView(UUID standId, int standNumber, String standName, long interactionCount) {
    }

    public record AnalyticsSummaryView(
            long totalSearches,
            long totalInteractions,
            List<SearchQueryStatView> topSearchedQueries,
            List<SearchQueryStatView> zeroResultQueries,
            List<MenuItemClickStatView> topClickedMenuItems,
            List<StandClickStatView> topClickedStands
    ) {
    }

    private static boolean matchesFilters(MenuItemEntity item, MenuKind kindFilter, boolean onlyAvailable) {
        if (kindFilter != null && item.getKind() != kindFilter) {
            return false;
        }
        if (onlyAvailable && (item.getAvailablePlates() == null || item.getAvailablePlates() <= 0)) {
            return false;
        }
        return true;
    }

    private static int scoreMenuItem(MenuItemEntity item, String fullQuery, List<String> tokens) {
        String nameNorm = normalizeText(item.getName());
        String descItNorm = normalizeText(item.getDescriptionIt());
        String descEnNorm = normalizeText(item.getDescriptionEn());
        List<String> keywordsNorm = item.getKeywords() == null
                ? List.of()
                : item.getKeywords().stream().map(StandService::normalizeText).filter(s -> !s.isEmpty()).toList();

        String[] nameWords = nameNorm.isEmpty() ? new String[0] : nameNorm.split("\\s+");
        String[] descItWords = descItNorm.isEmpty() ? new String[0] : descItNorm.split("\\s+");

        int score = 0;

        // Match frase intera sul nome del piatto o su una keyword
        if (!fullQuery.isEmpty()) {
            if (nameNorm.equals(fullQuery)) {
                score += 150;
            } else if (nameNorm.contains(fullQuery)) {
                score += 90;
            }
            if (keywordsNorm.contains(fullQuery)) {
                score += 130;
            }
        }

        int matchedTokens = 0;
        for (String token : tokens) {
            int tokenScore = 0;
            String tokenStem = italianStem(token);
            java.util.Set<String> synonyms = CULINARY_SYNONYMS.getOrDefault(token, java.util.Set.of());

            // 1. Nome piatto (esatto, prefisso/stem, sottostringa o fuzzy typo)
            if (nameNorm.equals(token)) {
                tokenScore = Math.max(tokenScore, 100);
            } else if (containsWordPrefixOrStem(nameWords, token, tokenStem)) {
                tokenScore = Math.max(tokenScore, 80);
            } else if (nameNorm.contains(token)) {
                tokenScore = Math.max(tokenScore, 60);
            } else if (matchesFuzzyWord(nameWords, token)) {
                tokenScore = Math.max(tokenScore, 55);
            }

            // 2. Parole chiave del piatto (esatto, prefisso/stem, fuzzy typo)
            for (String kw : keywordsNorm) {
                if (kw.equals(token)) {
                    tokenScore = Math.max(tokenScore, 100);
                } else if (kw.startsWith(token) || token.startsWith(kw) || italianStem(kw).equals(tokenStem)) {
                    tokenScore = Math.max(tokenScore, 75);
                } else if (kw.contains(token)) {
                    tokenScore = Math.max(tokenScore, 55);
                } else if (isFuzzyMatch(kw, token)) {
                    tokenScore = Math.max(tokenScore, 50);
                }
            }

            // 3. Espansione sinonimi gastronomici (es. cerco "pasta" o "primo" e trova "bucatini"/"fettuccine")
            if (tokenScore < 65 && !synonyms.isEmpty()) {
                for (String syn : synonyms) {
                    if (keywordsNorm.contains(syn) || containsWordPrefixOrStem(nameWords, syn, italianStem(syn))) {
                        tokenScore = Math.max(tokenScore, 65);
                        break;
                    }
                    if (containsWordPrefixOrStem(descItWords, syn, italianStem(syn))) {
                        tokenScore = Math.max(tokenScore, 45);
                    }
                }
            }

            // 4. Descrizione IT / EN del piatto (esatto/stem o fuzzy)
            if (containsWordPrefixOrStem(descItWords, token, tokenStem) || containsWordPrefix(descEnNorm, token)) {
                tokenScore = Math.max(tokenScore, 40);
            } else if (descItNorm.contains(token) || descEnNorm.contains(token)) {
                tokenScore = Math.max(tokenScore, 25);
            } else if (matchesFuzzyWord(descItWords, token)) {
                tokenScore = Math.max(tokenScore, 20);
            }

            if (tokenScore > 0) {
                matchedTokens++;
                score += tokenScore;
            }
        }

        if (score == 0) {
            return 0;
        }

        // Bonus se tutti i token cercati compaiono nel piatto (es. "pasta guanciale")
        if (tokens.size() > 1 && matchedTokens == tokens.size()) {
            score += 40;
        }

        // Bonus disponibilità: premia i piatti non esauriti
        if (item.getAvailablePlates() != null && item.getAvailablePlates() > 0) {
            score += 15;
        }

        return score;
    }

    private static int scoreStandMetadata(StandEntity stand, String fullQuery, List<String> tokens) {
        String standNameNorm = normalizeText(stand.getName());
        String standDescItNorm = normalizeText(stand.getDescriptionIt());
        String standDescEnNorm = normalizeText(stand.getDescriptionEn());
        String standNumberStr = String.valueOf(stand.getNumber());
        String[] standNameWords = standNameNorm.isEmpty() ? new String[0] : standNameNorm.split("\\s+");

        int score = 0;
        if (standNumberStr.equals(fullQuery)) {
            score += 120;
        } else if (!fullQuery.isEmpty() && standNameNorm.equals(fullQuery)) {
            score += 110;
        }

        for (String token : tokens) {
            if (standNumberStr.equals(token)) {
                score += 80;
            } else if (containsWordPrefix(standNameNorm, token)) {
                score += 50;
            } else if (standNameNorm.contains(token) || matchesFuzzyWord(standNameWords, token)) {
                score += 35;
            } else if (standDescItNorm.contains(token) || standDescEnNorm.contains(token)) {
                score += 15;
            }
        }
        return score;
    }

    /**
     * Calcola un bonus (0..20 punti) basato sulla distanza in linea d'aria (Haversine)
     * tra la posizione GPS dell'utente e la cantina ad Arce.
     */
    private static int computeProximityBonus(StandEntity stand, Double userLat, Double userLng) {
        if (userLat == null || userLng == null || stand.getLatitude() == null || stand.getLongitude() == null) {
            return 0;
        }
        double distMeters = haversineMeters(userLat, userLng, stand.getLatitude(), stand.getLongitude());
        if (distMeters <= 50) return 20;
        if (distMeters <= 150) return 12;
        if (distMeters <= 350) return 6;
        return 0;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Arricchisce automaticamente le keyword esplicite di un piatto estraendo i sostantivi/ingredienti
     * dal nome e dalla descrizione italiana più i sinonimi di categoria (es. "Amatriciana" -> aggiunge
     * automaticamente "pasta", "primo", "sugo", "guanciale" se non già presenti).
     */
    static List<String> enrichKeywordsWithAutoSuggestions(
            String name,
            String descriptionIt,
            List<String> explicitKeywords
    ) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (explicitKeywords != null) {
            for (String kw : explicitKeywords) {
                String norm = normalizeText(kw);
                if (!norm.isEmpty()) {
                    merged.add(norm);
                }
            }
        }

        String combinedText = normalizeText((name != null ? name : "") + " " + (descriptionIt != null ? descriptionIt : ""));
        for (String word : combinedText.split("\\s+")) {
            if (word.length() >= 4 && !STOP_WORDS.contains(word)) {
                merged.add(word);
                java.util.Set<String> related = CULINARY_SYNONYMS.get(word);
                if (related != null) {
                    merged.addAll(related);
                }
            }
        }
        return new java.util.ArrayList<>(merged);
    }

    private static boolean containsWordPrefix(String normalizedText, String token) {
        if (normalizedText.isEmpty() || token.isEmpty()) {
            return false;
        }
        for (String word : normalizedText.split("\\s+")) {
            if (word.startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWordPrefixOrStem(String[] words, String token, String tokenStem) {
        if (words.length == 0 || token.isEmpty()) {
            return false;
        }
        for (String w : words) {
            if (w.startsWith(token) || (tokenStem.length() >= 4 && italianStem(w).equals(tokenStem))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stemmer morfologico leggero per l'italiano: rimuove la vocale finale (o/a/i/e)
     * per unificare singolare/plurale (es. "castagna" <-> "castagne", "pizzella" <-> "pizzelle").
     */
    static String italianStem(String word) {
        if (word == null || word.length() < 5) {
            return word == null ? "" : word;
        }
        if (word.endsWith("che") || word.endsWith("chi")) {
            return word.substring(0, word.length() - 3) + "c";
        }
        if (word.endsWith("ghe") || word.endsWith("ghi")) {
            return word.substring(0, word.length() - 3) + "g";
        }
        char last = word.charAt(word.length() - 1);
        if (last == 'a' || last == 'e' || last == 'i' || last == 'o') {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static boolean matchesFuzzyWord(String[] words, String token) {
        if (token.length() < 4) {
            return false;
        }
        for (String w : words) {
            if (isFuzzyMatch(w, token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tolleranza errori di battitura tramite distanza di Damerau-Levenshtein
     * (include trasposizione di due lettere adiacenti, es. "castange" -> "castagne",
     * oppure lettere doppie/mancanti come "amatricciana" -> "amatriciana").
     */
    static boolean isFuzzyMatch(String candidate, String queryToken) {
        if (candidate == null || queryToken == null) return false;
        int len1 = candidate.length();
        int len2 = queryToken.length();
        if (len1 < 4 || len2 < 4) return false;
        int maxDist = (Math.min(len1, len2) >= 7) ? 2 : 1;
        if (Math.abs(len1 - len2) > maxDist) return false;
        return damerauLevenshtein(candidate, queryToken) <= maxDist;
    }

    static int damerauLevenshtein(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int[][] dp = new int[lenA + 1][lenB + 1];

        for (int i = 0; i <= lenA; i++) dp[i][0] = i;
        for (int j = 0; j <= lenB; j++) dp[0][j] = j;

        for (int i = 1; i <= lenA; i++) {
            for (int j = 1; j <= lenB; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + 1);
                }
            }
        }
        return dp[lenA][lenB];
    }

    static List<String> tokenizeQuery(String query) {
        String normalized = normalizeText(query);
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] rawTokens = normalized.split("\\s+");
        List<String> filtered = java.util.Arrays.stream(rawTokens)
                .filter(t -> !t.isEmpty() && (!STOP_WORDS.contains(t) || rawTokens.length == 1))
                .distinct()
                .toList();
        return filtered.isEmpty() ? List.of(normalized) : filtered;
    }

    static String normalizeText(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String lower = java.text.Normalizer.normalize(input.toLowerCase(java.util.Locale.ITALIAN), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return lower.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }
}
