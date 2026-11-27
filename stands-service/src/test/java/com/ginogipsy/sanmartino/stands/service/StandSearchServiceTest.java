package com.ginogipsy.sanmartino.stands.service;

import com.ginogipsy.sanmartino.observability.audit.AuditUserResolver;
import com.ginogipsy.sanmartino.observability.audit.AuditUserResolver.AuditUser;
import com.ginogipsy.sanmartino.stands.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StandSearchServiceTest {

    @Mock
    private StandRepository repository;

    @Mock
    private StandSearchLogRepository searchLogRepository;

    @Mock
    private StandInteractionEventRepository interactionEventRepository;

    @Mock
    private FestivalReviewRepository reviewRepository;

    @Mock
    private UserFavoriteStandRepository favoriteStandRepository;

    @Mock
    private AuditUserResolver auditUserResolver;

    private StandService service;

    @BeforeEach
    void setUp() {
        service = new StandService(
                repository,
                searchLogRepository,
                interactionEventRepository,
                reviewRepository,
                favoriteStandRepository,
                auditUserResolver
        );
    }

    private static StandEntity stand(int number, String name, String descIt, MenuItemEntity... items) {
        StandEntity s = new StandEntity(
                UUID.randomUUID(),
                number,
                name,
                descIt,
                descIt,
                2018,
                41.5822,
                13.5775
        );
        for (MenuItemEntity item : items) {
            if (item.getId() == null) {
                item.setId(UUID.randomUUID());
            }
            s.addMenuItem(item);
        }
        return s;
    }

    @Test
    void findsCantinaByDishKeywordAndRanksExactKeywordMatchHighest() {
        StandEntity cantinaAmatriciana = stand(
                10,
                "Cantina del Corso",
                "Cucina tipica ciociara",
                new MenuItemEntity(
                        UUID.randomUUID(),
                        "Amatriciana della tradizione",
                        "Bucatini con guanciale croccante, sugo di pomodoro e pecorino",
                        "Bucatini with crispy guanciale, tomato sauce and pecorino",
                        45,
                        MenuKind.FOOD,
                        List.of("pasta", "primo", "sugo", "guanciale")
                )
        );

        StandEntity cantinaCastagne = stand(
                1,
                "Led Zeppelin",
                "Castagne e vino novello",
                new MenuItemEntity(
                        UUID.randomUUID(),
                        "Castagne arrostite",
                        "Castagne cotte sulla brace",
                        "Roasted chestnuts",
                        80,
                        MenuKind.FOOD,
                        List.of("castagne", "brace", "autunno")
                )
        );

        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(cantinaCastagne, cantinaAmatriciana));

        // L'utente cerca "pasta": anche se il piatto si chiama "Amatriciana della tradizione",
        // viene trovato grazie alla keyword "pasta"
        List<StandService.StandSearchHit> results = service.searchStands("pasta", null, false);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().stand().getName()).isEqualTo("Cantina del Corso");
        assertThat(results.getFirst().matchedItems())
                .extracting(MenuItemEntity::getName)
                .containsExactly("Amatriciana della tradizione");
        assertThat(results.getFirst().score()).isGreaterThan(100);
    }

    @Test
    void multiWordSearchIgnoresStopWordsAndRewardsMatchingAllTokens() {
        StandEntity cantinaAmatriciana = stand(
                10,
                "Cantina del Corso",
                "Piatti caldi",
                new MenuItemEntity(
                        UUID.randomUUID(),
                        "Amatriciana",
                        "Bucatini classici",
                        "Classic bucatini",
                        30,
                        MenuKind.FOOD,
                        List.of("pasta", "primo", "sugo", "guanciale")
                )
        );

        StandEntity cantinaFettuccineFunghi = stand(
                12,
                "La Locanda",
                "Specialità funghi",
                new MenuItemEntity(
                        UUID.randomUUID(),
                        "Fettuccine ai funghi porcini",
                        "Fettuccine fatte a mano con porcini",
                        "Handmade fettuccine with porcini",
                        30,
                        MenuKind.FOOD,
                        List.of("pasta", "primo", "funghi")
                )
        );

        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(cantinaFettuccineFunghi, cantinaAmatriciana));

        // "pasta al sugo" -> tokenizza in ["pasta", "sugo"] ignorando la stopword "al"
        List<StandService.StandSearchHit> results = service.searchStands("pasta al sugo", null, false);

        assertThat(results).hasSize(2);
        // L'Amatriciana matcha sia "pasta" che "sugo", quindi deve stare al primo posto
        assertThat(results.get(0).stand().getName()).isEqualTo("Cantina del Corso");
        assertThat(results.get(1).stand().getName()).isEqualTo("La Locanda");
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
    }

    @Test
    void filtersByKindAndOnlyAvailablePlates() {
        StandEntity cantina = stand(
                5,
                "Cantina del Borgo",
                "Cibo e vino",
                new MenuItemEntity(
                        UUID.randomUUID(),
                        "Polenta con salsiccia",
                        "Polenta calda",
                        "Hot polenta",
                        0, // Esaurito!
                        MenuKind.FOOD,
                        List.of("polenta", "salsiccia")
                ),
                new MenuItemEntity(
                        UUID.randomUUID(),
                        "Vino rosso locale",
                        "Calice di vino",
                        "Red wine glass",
                        25,
                        MenuKind.DRINK,
                        List.of("vino", "rosso")
                )
        );

        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(cantina));

        // Cerca "polenta" con onlyAvailable=true -> nessun risultato perché availablePlates == 0
        assertThat(service.searchStands("polenta", null, true)).isEmpty();

        // Cerca "polenta" con onlyAvailable=false -> trovato
        assertThat(service.searchStands("polenta", null, false)).hasSize(1);

        // Cerca "vino" filtrando per FOOD -> vuoto, filtrando per DRINK -> trovato
        assertThat(service.searchStands("vino", MenuKind.FOOD, false)).isEmpty();
        assertThat(service.searchStands("vino", MenuKind.DRINK, true)).hasSize(1);
    }

    @Test
    void toleratesTyposViaDamerauLevenshteinAndMatchesItalianSingularPlural() {
        StandEntity cantina = stand(
                1,
                "Led Zeppelin",
                "Tradizione autunnale",
                new MenuItemEntity(
                        UUID.randomUUID(),
                        "Castagne arrostite",
                        "Castagne cotte sulla brace",
                        "Roasted chestnuts",
                        50,
                        MenuKind.FOOD,
                        List.of("castagne", "brace")
                ),
                new MenuItemEntity(
                        UUID.randomUUID(),
                        "Amatriciana",
                        "Bucatini col guanciale",
                        "Bucatini with guanciale",
                        30,
                        MenuKind.FOOD,
                        List.of("pasta", "guanciale")
                )
        );

        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(cantina));

        // Errore di battitura con trasposizione lettere ("castange") -> trova "Castagne arrostite"
        List<StandService.StandSearchHit> typoCastagne = service.searchStands("castange", null, false);
        assertThat(typoCastagne).hasSize(1);
        assertThat(typoCastagne.getFirst().matchedItems())
                .extracting(MenuItemEntity::getName)
                .contains("Castagne arrostite");

        // Errore di battitura con doppia lettera ("amatricciana") -> trova "Amatriciana"
        List<StandService.StandSearchHit> typoAmatriciana = service.searchStands("amatricciana", null, false);
        assertThat(typoAmatriciana).hasSize(1);
        assertThat(typoAmatriciana.getFirst().matchedItems())
                .extracting(MenuItemEntity::getName)
                .contains("Amatriciana");

        // Singolare ("castagna") -> trova il plurale "castagne" tramite stemmer italiano
        List<StandService.StandSearchHit> singularHit = service.searchStands("castagna", null, false);
        assertThat(singularHit).hasSize(1);
    }

    @Test
    void expandsCulinarySynonymsAndAutoEnrichesKeywords() {
        // Piatto inserito senza keyword esplicite: il sistema estrae e arricchisce automaticamente
        List<String> autoKeywords = StandService.enrichKeywordsWithAutoSuggestions(
                "Amatriciana della casa",
                "Bucatini con guanciale croccante",
                List.of()
        );
        assertThat(autoKeywords).contains("amatriciana", "pasta", "primo", "sugo", "guanciale", "bucatini");

        // Sinonimi in ricerca: il piatto ha solo "bucatini" nel nome e nessuna keyword "pasta",
        // ma cercando "pasta" o "primo" viene trovato grazie all'espansione sinonimi
        StandEntity cantina = stand(
                8,
                "Cantina Romana",
                "Cucina laziale",
                new MenuItemEntity(
                        UUID.randomUUID(),
                        "Bucatini del pastore",
                        "Ricetta antica",
                        "Ancient recipe",
                        20,
                        MenuKind.FOOD,
                        List.of()
                )
        );
        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(cantina));

        assertThat(service.searchStands("pasta", null, false)).hasSize(1);
        assertThat(service.searchStands("primo", null, false)).hasSize(1);
    }

    @Test
    void logsSearchQueriesAndBoostsMostClickedMenuItems() {
        MenuItemEntity amatriciana1 = new MenuItemEntity(
                UUID.randomUUID(),
                "Amatriciana",
                "Bucatini",
                "Bucatini",
                30,
                MenuKind.FOOD,
                List.of("pasta")
        );
        MenuItemEntity amatriciana2 = new MenuItemEntity(
                UUID.randomUUID(),
                "Amatriciana",
                "Bucatini",
                "Bucatini",
                30,
                MenuKind.FOOD,
                List.of("pasta")
        );

        StandEntity cantina1 = stand(1, "Cantina Uno", "Cucina", amatriciana1);
        StandEntity cantina2 = stand(2, "Cantina Due", "Cucina", amatriciana2);

        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(cantina1, cantina2));

        // Simula 40 click sul piatto della Cantina Due
        StandInteractionEventRepository.MenuItemClickAggregation clickAgg =
                new StandInteractionEventRepository.MenuItemClickAggregation() {
                    @Override
                    public UUID getMenuItemId() {
                        return amatriciana2.getId();
                    }

                    @Override
                    public long getClickCount() {
                        return 40L;
                    }
                };
        when(interactionEventRepository.findAllMenuItemClickCounts()).thenReturn(List.of(clickAgg));

        List<StandService.StandSearchHit> hits = service.searchStands("  Amatriciana! ", MenuKind.FOOD, true);

        // Grazie al popularity boost, Cantina Due precede Cantina Uno a parità di testo e disponibilità
        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).stand().getName()).isEqualTo("Cantina Due");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());

        // Verifica che la ricerca sia stata salvata in stand_search_logs
        ArgumentCaptor<StandSearchLogEntity> logCaptor = ArgumentCaptor.forClass(StandSearchLogEntity.class);
        verify(searchLogRepository).save(logCaptor.capture());
        StandSearchLogEntity savedLog = logCaptor.getValue();
        assertThat(savedLog.getRawQuery()).isEqualTo("Amatriciana!");
        assertThat(savedLog.getNormalizedQuery()).isEqualTo("amatriciana");
        assertThat(savedLog.getKindFilter()).isEqualTo(MenuKind.FOOD);
        assertThat(savedLog.isOnlyAvailable()).isTrue();
        assertThat(savedLog.getResultsCount()).isEqualTo(2);
    }

    @Test
    void doesNotLogBlankOrSingleCharacterQueriesAndRecordsInteractions() {
        MenuItemEntity piatto = new MenuItemEntity(
                UUID.randomUUID(),
                "Polenta",
                "Polenta",
                "Polenta",
                10,
                MenuKind.FOOD,
                List.of("polenta")
        );
        StandEntity cantina = stand(4, "La Polenteria", "Polenta", piatto);
        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(cantina));
        when(repository.findById(cantina.getId())).thenReturn(Optional.of(cantina));

        service.searchStands("a", null, false);
        verify(searchLogRepository, never()).save(any());

        service.recordInteraction(
                cantina.getId(),
                piatto.getId(),
                StandInteractionType.MENU_ITEM_CLICK,
                "Polenta calda"
        );

        ArgumentCaptor<StandInteractionEventEntity> eventCaptor =
                ArgumentCaptor.forClass(StandInteractionEventEntity.class);
        verify(interactionEventRepository).save(eventCaptor.capture());
        StandInteractionEventEntity savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getStandId()).isEqualTo(cantina.getId());
        assertThat(savedEvent.getMenuItemId()).isEqualTo(piatto.getId());
        assertThat(savedEvent.getInteractionType()).isEqualTo(StandInteractionType.MENU_ITEM_CLICK);
        assertThat(savedEvent.getSearchQuery()).isEqualTo("polenta calda");
    }

    @Test
    void aggregatesAnalyticsSummaryWithTopSearchesZeroResultsAndTopClickedDishes() {
        MenuItemEntity piatto = new MenuItemEntity(
                UUID.randomUUID(),
                "Sagnozzi al tartufo",
                "Pasta fresca al tartufo",
                "Fresh pasta with truffle",
                25,
                MenuKind.FOOD,
                List.of("pasta", "tartufo")
        );
        StandEntity cantina = stand(7, "Il Tartufo del Tobia", "Specialità tartufo", piatto);

        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(cantina));
        when(searchLogRepository.count()).thenReturn(15L);
        when(interactionEventRepository.count()).thenReturn(28L);

        Instant now = Instant.now();
        when(searchLogRepository.findTopSearchedQueries(any())).thenReturn(List.of(
                new StandSearchLogRepository.SearchQueryAggregation() {
                    @Override
                    public String getQuery() {
                        return "pasta";
                    }

                    @Override
                    public long getSearchCount() {
                        return 10L;
                    }

                    @Override
                    public double getAvgResultsCount() {
                        return 3.4;
                    }

                    @Override
                    public Instant getLastSearchedAt() {
                        return now;
                    }
                }
        ));
        when(searchLogRepository.findTopZeroResultQueries(any())).thenReturn(List.of(
                new StandSearchLogRepository.SearchQueryAggregation() {
                    @Override
                    public String getQuery() {
                        return "sushi";
                    }

                    @Override
                    public long getSearchCount() {
                        return 5L;
                    }

                    @Override
                    public double getAvgResultsCount() {
                        return 0.0;
                    }

                    @Override
                    public Instant getLastSearchedAt() {
                        return now;
                    }
                }
        ));
        when(interactionEventRepository.findTopClickedMenuItems(any())).thenReturn(List.of(
                new StandInteractionEventRepository.MenuItemClickAggregation() {
                    @Override
                    public UUID getMenuItemId() {
                        return piatto.getId();
                    }

                    @Override
                    public long getClickCount() {
                        return 19L;
                    }
                }
        ));
        when(interactionEventRepository.findTopClickedStands(any())).thenReturn(List.of(
                new StandInteractionEventRepository.StandClickAggregation() {
                    @Override
                    public UUID getStandId() {
                        return cantina.getId();
                    }

                    @Override
                    public long getInteractionCount() {
                        return 28L;
                    }
                }
        ));

        StandService.AnalyticsSummaryView summary = service.getAnalyticsSummary(10);

        assertThat(summary.totalSearches()).isEqualTo(15L);
        assertThat(summary.totalInteractions()).isEqualTo(28L);
        assertThat(summary.topSearchedQueries()).hasSize(1);
        assertThat(summary.topSearchedQueries().getFirst().query()).isEqualTo("pasta");
        assertThat(summary.zeroResultQueries()).hasSize(1);
        assertThat(summary.zeroResultQueries().getFirst().query()).isEqualTo("sushi");
        assertThat(summary.topClickedMenuItems()).hasSize(1);
        assertThat(summary.topClickedMenuItems().getFirst().menuItemName()).isEqualTo("Sagnozzi al tartufo");
        assertThat(summary.topClickedMenuItems().getFirst().clickCount()).isEqualTo(19L);
        assertThat(summary.topClickedStands()).hasSize(1);
        assertThat(summary.topClickedStands().getFirst().standName()).isEqualTo("Il Tartufo del Tobia");
    }

    @Test
    void createsPendingReviewsModeratesThemAndBoostsApprovedHighRatedDishes() {
        MenuItemEntity pizzella1 = new MenuItemEntity(
                UUID.randomUUID(),
                "Pizzella fritta",
                "Pizzella classica",
                "Classic fried dough",
                40,
                MenuKind.FOOD,
                List.of("pizzelle")
        );
        MenuItemEntity pizzella2 = new MenuItemEntity(
                UUID.randomUUID(),
                "Pizzella fritta",
                "Pizzella classica",
                "Classic fried dough",
                40,
                MenuKind.FOOD,
                List.of("pizzelle")
        );

        StandEntity cantina1 = stand(1, "Cantina Uno", "Pizzelle", pizzella1);
        StandEntity cantina2 = stand(2, "Cantina Due", "Pizzelle", pizzella2);

        when(repository.findById(cantina2.getId())).thenReturn(Optional.of(cantina2));
        when(auditUserResolver.resolveUser()).thenReturn(new AuditUser("user-123", "francesco"));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 1. Invio recensione da utente autenticato -> stato iniziale PENDING
        FestivalReviewEntity created = service.createReview(
                ReviewTargetType.MENU_ITEM,
                pizzella2.getId(),
                cantina2.getId(),
                null,
                null,
                5,
                "Pizzella buonissima, calda e croccante!"
        );

        assertThat(created.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(created.getAuthorUsername()).isEqualTo("francesco");
        assertThat(created.getTargetName()).contains("Pizzella fritta").contains("Cantina Due");
        assertThat(created.getRating()).isEqualTo(5);

        // 2. Moderazione della recensione -> APPROVED
        when(reviewRepository.findById(created.getId())).thenReturn(Optional.of(created));
        when(auditUserResolver.resolveUser()).thenReturn(new AuditUser("mod-1", "moderatore_arce"));

        FestivalReviewEntity moderated = service.moderateReview(
                created.getId(),
                ReviewStatus.APPROVED,
                "Recensione verificata e conforme"
        );

        assertThat(moderated.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(moderated.getModeratedBy()).isEqualTo("moderatore_arce");
        assertThat(moderated.getModeratedAt()).isNotNull();

        // 3. Il rating medio approvato (5.0 stelle) assegna un boost (+20 punti) al piatto di Cantina Due
        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(cantina1, cantina2));
        when(reviewRepository.findApprovedMenuItemRatings()).thenReturn(List.of(
                new FestivalReviewRepository.TargetRatingAggregation() {
                    @Override
                    public UUID getTargetId() {
                        return pizzella2.getId();
                    }

                    @Override
                    public double getAvgRating() {
                        return 5.0;
                    }

                    @Override
                    public long getReviewCount() {
                        return 4L;
                    }
                }
        ));

        List<StandService.StandSearchHit> hits = service.searchStands("pizzella", MenuKind.FOOD, false);
        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).stand().getName()).isEqualTo("Cantina Due");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }

    @Test
    void addsListsAndRemovesFavoriteStandsForAuthenticatedUser() {
        UUID standId = UUID.randomUUID();
        when(repository.existsById(standId)).thenReturn(true);
        when(auditUserResolver.resolveUser()).thenReturn(new AuditUser("user-42", "helloworld"));
        when(favoriteStandRepository.findByUserIdAndStandId("user-42", standId)).thenReturn(Optional.empty());

        service.addFavoriteStand(standId);

        ArgumentCaptor<UserFavoriteStandEntity> favCaptor = ArgumentCaptor.forClass(UserFavoriteStandEntity.class);
        verify(favoriteStandRepository).save(favCaptor.capture());
        UserFavoriteStandEntity savedFav = favCaptor.getValue();
        assertThat(savedFav.getUserId()).isEqualTo("user-42");
        assertThat(savedFav.getUsername()).isEqualTo("helloworld");
        assertThat(savedFav.getStandId()).isEqualTo(standId);

        when(favoriteStandRepository.findAllByUserIdOrderByCreatedAtDesc("user-42"))
                .thenReturn(List.of(savedFav));

        List<UUID> favorites = service.listFavoriteStandIds();
        assertThat(favorites).containsExactly(standId);

        service.removeFavoriteStand(standId);
        verify(favoriteStandRepository).deleteByUserIdAndStandId("user-42", standId);
    }

    @Test
    void manages10SlotKeypadOrdersKitchenDeliveryLedsAndGranularStaffPermissions() {
        StandKeypadConfigRepository configRepo = mock(StandKeypadConfigRepository.class);
        StandKeypadSlotRepository slotRepo = mock(StandKeypadSlotRepository.class);
        StandStaffPermissionRepository permRepo = mock(StandStaffPermissionRepository.class);
        StandOrderRepository orderRepo = mock(StandOrderRepository.class);

        StandKeypadService keypadService = new StandKeypadService(
                repository,
                configRepo,
                slotRepo,
                permRepo,
                orderRepo,
                auditUserResolver
        );

        MenuItemEntity piatto1 = new MenuItemEntity(
                UUID.randomUUID(),
                "Sagnozzi al Tartufo",
                "Primo piatto",
                "First course",
                20,
                MenuKind.FOOD,
                List.of("pasta")
        );
        MenuItemEntity piatto2 = new MenuItemEntity(
                UUID.randomUUID(),
                "Arrosticini",
                "Secondo piatto",
                "Main course",
                30,
                MenuKind.FOOD,
                List.of("carne")
        );
        MenuItemEntity vino = new MenuItemEntity(
                UUID.randomUUID(),
                "Calice Cesanese",
                "Vino rosso",
                "Red wine",
                50,
                MenuKind.DRINK,
                List.of("vino")
        );

        StandEntity ginoCantina = stand(12, "Gino Cantina", "Cantina storica", piatto1, piatto2, vino);
        ginoCantina.addOwner(new StandOwnerEntity(
                UUID.randomUUID(),
                "Francesco",
                "Grossi",
                "francesco-grossi@sanmartino.it",
                null,
                OwnerRole.PRIMARY
        ));

        when(repository.findById(ginoCantina.getId())).thenReturn(Optional.of(ginoCantina));
        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(ginoCantina));

        java.util.concurrent.atomic.AtomicReference<StandKeypadConfigEntity> savedConfig =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(configRepo.findById(ginoCantina.getId())).thenAnswer(inv -> Optional.ofNullable(savedConfig.get()));
        when(configRepo.save(any())).thenAnswer(inv -> {
            StandKeypadConfigEntity c = inv.getArgument(0);
            savedConfig.set(c);
            return c;
        });

        List<StandKeypadSlotEntity> storedSlots = new java.util.ArrayList<>();
        when(slotRepo.findAllByStandIdOrderBySlotNumberAsc(ginoCantina.getId()))
                .thenAnswer(inv -> storedSlots.stream()
                        .sorted(java.util.Comparator.comparingInt(StandKeypadSlotEntity::getSlotNumber))
                        .toList());
        when(slotRepo.save(any())).thenAnswer(inv -> {
            StandKeypadSlotEntity s = inv.getArgument(0);
            storedSlots.removeIf(existing -> existing.getSlotNumber() == s.getSlotNumber());
            storedSlots.add(s);
            return s;
        });

        List<StandStaffPermissionEntity> storedPerms = new java.util.ArrayList<>();
        when(permRepo.findAllByStandIdOrderByUsernameAscPermissionAsc(ginoCantina.getId()))
                .thenAnswer(inv -> List.copyOf(storedPerms));
        when(permRepo.findByStandIdAndUsernameIgnoreCaseAndPermission(any(), any(), any()))
                .thenAnswer(inv -> {
                    String u = inv.getArgument(1);
                    StandStaffRole r = inv.getArgument(2);
                    return storedPerms.stream()
                            .filter(p -> p.getUsername().equalsIgnoreCase(u) && p.getPermission() == r)
                            .findFirst();
                });
        when(permRepo.save(any())).thenAnswer(inv -> {
            StandStaffPermissionEntity p = inv.getArgument(0);
            storedPerms.add(p);
            return p;
        });

        List<StandOrderEntity> storedOrders = new java.util.ArrayList<>();
        when(orderRepo.findAllByStandIdAndStatusInOrderByOrderNumberAsc(eq(ginoCantina.getId()), any()))
                .thenAnswer(inv -> {
                    java.util.Collection<StandOrderStatus> statuses = inv.getArgument(1);
                    return storedOrders.stream()
                            .filter(o -> statuses.contains(o.getStatus()))
                            .sorted(java.util.Comparator.comparingInt(StandOrderEntity::getOrderNumber))
                            .toList();
                });
        when(orderRepo.findTop10ByStandIdAndStatusOrderByOrderNumberDesc(eq(ginoCantina.getId()), eq(StandOrderStatus.DELIVERED)))
                .thenAnswer(inv -> storedOrders.stream()
                        .filter(o -> o.getStatus() == StandOrderStatus.DELIVERED)
                        .sorted(java.util.Comparator.comparingInt(StandOrderEntity::getOrderNumber).reversed())
                        .limit(10)
                        .toList());
        when(orderRepo.save(any())).thenAnswer(inv -> {
            StandOrderEntity o = inv.getArgument(0);
            storedOrders.removeIf(existing -> existing.getId().equals(o.getId()));
            storedOrders.add(o);
            return o;
        });

        // 1. Francesco Grossi (Stand Owner di gino-cantina) configura i 10 slot e assegna i ruoli:
        //    - maria-chiara -> CASHIER (cassa)
        //    - scricci      -> DELIVERY (consegna)
        //    - lisa-la      -> VIEWER (lettura ordini)
        when(auditUserResolver.resolveUser()).thenReturn(new AuditUser("u-owner", "francesco-grossi"));

        StandKeypadService.KeypadStateView initial = keypadService.updateSlots(
                ginoCantina.getId(),
                List.of(
                        new StandKeypadService.SlotAssignmentInput(1, piatto1.getId()),
                        new StandKeypadService.SlotAssignmentInput(2, piatto2.getId()),
                        new StandKeypadService.SlotAssignmentInput(7, vino.getId())
                ),
                false
        );
        assertThat(initial.slots()).hasSize(10);
        assertThat(initial.userPermissions()).contains(StandStaffRole.OWNER, StandStaffRole.CASHIER, StandStaffRole.KITCHEN, StandStaffRole.DELIVERY);

        keypadService.addStaffPermission(ginoCantina.getId(), "maria-chiara", StandStaffRole.CASHIER);
        keypadService.addStaffPermission(ginoCantina.getId(), "scricci", StandStaffRole.DELIVERY);
        keypadService.addStaffPermission(ginoCantina.getId(), "lisa-la", StandStaffRole.VIEWER);

        // 2. Lisa-la (VIEWER) può leggere lo stato degli ordini di gino-cantina ma NON può inserire ordini in cassa
        when(auditUserResolver.resolveUser()).thenReturn(new AuditUser("u-lisa", "lisa-la"));
        assertThat(keypadService.listAccessibleStands()).hasSize(1);
        StandKeypadService.KeypadStateView lisaView = keypadService.getKeypadState(ginoCantina.getId(), null, null);
        assertThat(lisaView.userPermissions()).containsExactly(StandStaffRole.VIEWER);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                keypadService.createOrder(ginoCantina.getId(), List.of(new StandKeypadService.OrderItemInput(1, 1)), null)
        ).isInstanceOf(StandAccessDeniedException.class);

        // 3. Maria-Chiara (CASHIER) inserisce Ordine #1: Piatto 1 + Piatto 1 + Vino (Slot 1 x2, Slot 7 x1)
        //    e subito dopo Ordine #2: 1x Piatto 2 (Slot 2 x1)
        when(auditUserResolver.resolveUser()).thenReturn(new AuditUser("u-maria", "maria-chiara"));
        StandKeypadService.KeypadStateView afterOrder1 = keypadService.createOrder(
                ginoCantina.getId(),
                List.of(
                        new StandKeypadService.OrderItemInput(1, 1),
                        new StandKeypadService.OrderItemInput(1, 1),
                        new StandKeypadService.OrderItemInput(7, 1)
                ),
                null
        );
        assertThat(afterOrder1.lastIssuedOrderNumber()).isEqualTo(1);
        assertThat(afterOrder1.currentDeliveryOrderNumber()).isEqualTo(1);
        assertThat(piatto1.getAvailablePlates()).isEqualTo(18); // 20 - 2
        assertThat(vino.getAvailablePlates()).isEqualTo(49);    // 50 - 1

        StandKeypadService.KeypadStateView afterOrder2 = keypadService.createOrder(
                ginoCantina.getId(),
                List.of(new StandKeypadService.OrderItemInput(2, 1)),
                null
        );
        assertThat(afterOrder2.lastIssuedOrderNumber()).isEqualTo(2);
        assertThat(afterOrder2.activeOrdersCount()).isEqualTo(2);
        assertThat(afterOrder2.currentDeliveryOrderNumber()).isEqualTo(1);

        // Verifica stato LED prima della cucina:
        // - Slot 1 (Piatto 1): Cucina LED = ON (2 da cuocere), Consegna LED = BLINKING (in attesa cucina)
        // - Slot 7 (Vino, DRINK): già pronto al bancone -> Consegna LED = SOLID
        StandKeypadService.SlotView slot1BeforeCook = afterOrder2.slots().get(0);
        StandKeypadService.SlotView slot7BeforeCook = afterOrder2.slots().get(6);
        assertThat(slot1BeforeCook.kitchenLedOn()).isTrue();
        assertThat(slot1BeforeCook.pendingToCookCount()).isEqualTo(2);
        assertThat(slot1BeforeCook.deliveryLedState()).isEqualTo(DeliveryLedState.BLINKING);
        assertThat(slot7BeforeCook.deliveryLedState()).isEqualTo(DeliveryLedState.SOLID);
        assertThat(
                new com.ginogipsy.sanmartino.stands.api.StandMapper()
                        .toStandKeypadState(afterOrder2)
                        .getSlots()
        ).hasSize(10);

        // 4. La Cucina (ESP32 #2 autenticata con deviceToken) preme 2 volte il Pulsante 1
        String deviceToken = initial.deviceToken();
        keypadService.pressKitchenSlot(ginoCantina.getId(), 1, 1, deviceToken);
        StandKeypadService.KeypadStateView afterKitchenCooked =
                keypadService.pressKitchenSlot(ginoCantina.getId(), 1, 1, deviceToken);

        StandKeypadService.SlotView slot1AfterCook = afterKitchenCooked.slots().get(0);
        assertThat(slot1AfterCook.kitchenLedOn()).isFalse(); // Tutte e 2 le porzioni di Piatto 1 sono state cucinate!
        assertThat(slot1AfterCook.deliveryLedState()).isEqualTo(DeliveryLedState.SOLID);

        // 5. Scricci (DELIVERY) al bancone consegna ha Display = Ordine #1, LED 1 e LED 7 accesi.
        when(auditUserResolver.resolveUser()).thenReturn(new AuditUser("u-scricci", "scricci"));

        // Scricci preme Pulsante 1 (1a porzione di Piatto 1): LED 1 deve restare ACCESO (1/2 consegnato)
        StandKeypadService.KeypadStateView afterDeliver1 =
                keypadService.pressDeliverySlot(ginoCantina.getId(), 1, 1, null);
        assertThat(afterDeliver1.currentDeliveryOrderNumber()).isEqualTo(1);
        assertThat(afterDeliver1.slots().get(0).deliveryLedState()).isEqualTo(DeliveryLedState.SOLID);

        // Scricci ripreme Pulsante 1 (2a porzione di Piatto 1): ora LED 1 si SPEGNE (2/2 consegnati)!
        StandKeypadService.KeypadStateView afterDeliver2 =
                keypadService.pressDeliverySlot(ginoCantina.getId(), 1, 1, null);
        assertThat(afterDeliver2.currentDeliveryOrderNumber()).isEqualTo(1);
        assertThat(afterDeliver2.slots().get(0).deliveryLedState()).isEqualTo(DeliveryLedState.OFF);
        assertThat(afterDeliver2.slots().get(6).deliveryLedState()).isEqualTo(DeliveryLedState.SOLID);

        // Scricci preme Pulsante 7 (Vino): tutti gli item di Ordine #1 sono stati consegnati ->
        // passa AUTOMATICAMENTE a Ordine #2 e accende il LED dello Slot 2!
        StandKeypadService.KeypadStateView afterDeliverWine =
                keypadService.pressDeliverySlot(ginoCantina.getId(), 7, 1, null);
        assertThat(afterDeliverWine.lastDeliveredOrderNumber()).isEqualTo(1);
        assertThat(afterDeliverWine.currentDeliveryOrderNumber()).isEqualTo(2);
        assertThat(afterDeliverWine.activeOrdersCount()).isEqualTo(1);
        assertThat(afterDeliverWine.slots().get(0).deliveryLedState()).isEqualTo(DeliveryLedState.OFF);
        assertThat(afterDeliverWine.slots().get(6).deliveryLedState()).isEqualTo(DeliveryLedState.OFF);
        assertThat(afterDeliverWine.slots().get(1).deliveryLedState()).isNotEqualTo(DeliveryLedState.OFF);

        // 6. Maria-Chiara (CASHIER) annulla il singolo Ordine #2 rimasto in coda ->
        //    le porzioni di Piatto 2 (Arrosticini) tornano automaticamente da 29 a 30!
        UUID order2Id = afterDeliverWine.activeOrders().get(0).id();
        when(orderRepo.findById(order2Id)).thenAnswer(inv ->
                storedOrders.stream().filter(o -> o.getId().equals(order2Id)).findFirst()
        );
        assertThat(piatto2.getAvailablePlates()).isEqualTo(29);
        when(auditUserResolver.resolveUser()).thenReturn(new AuditUser("u-maria", "maria-chiara"));
        StandKeypadService.KeypadStateView afterCancelOrder2 =
                keypadService.cancelOrder(ginoCantina.getId(), order2Id);
        assertThat(afterCancelOrder2.activeOrdersCount()).isEqualTo(0);
        assertThat(piatto2.getAvailablePlates()).isEqualTo(30);
    }

    @Test
    void multiEventStandsMenusAndCrossEditionReviewContinuity() {
        UUID event2026 = UUID.fromString("11111111-1111-1111-1111-000000002026");
        UUID event2025 = UUID.fromString("11111111-1111-1111-1111-000000002025");
        UUID event2024 = UUID.fromString("11111111-1111-1111-1111-000000002024");

        StandEntity ledZeppelin = new StandEntity(
                UUID.randomUUID(),
                1,
                "Led Zeppelin",
                "Descrizione 2026",
                "Description 2026",
                2014,
                41.5822,
                13.5775
        );
        ledZeppelin.addParticipation(new StandEventParticipationEntity(
                UUID.randomUUID(),
                event2026,
                1,
                "Descrizione 2026",
                "Description 2026",
                41.5822,
                13.5775
        ));
        ledZeppelin.addParticipation(new StandEventParticipationEntity(
                UUID.randomUUID(),
                event2025,
                1,
                "Edizione 2025 Decennale",
                "2025 10th Anniversary",
                41.5822,
                13.5775
        ));

        // Piatto condiviso tra 2025 e 2026 (stesso canonicalItemId)
        UUID castagne2026Id = UUID.randomUUID();
        UUID castagne2025Id = UUID.randomUUID();
        MenuItemEntity castagne2026 = new MenuItemEntity(
                castagne2026Id,
                event2026,
                castagne2026Id,
                "Castagne arrostite",
                "Caldarroste 2026",
                "Roasted chestnuts 2026",
                80,
                MenuKind.FOOD,
                List.of("castagne")
        );
        MenuItemEntity castagne2025 = new MenuItemEntity(
                castagne2025Id,
                event2025,
                castagne2026Id, // stesso canonicalItemId
                "Castagne arrostite",
                "Caldarroste 2025",
                "Roasted chestnuts 2025",
                0,
                MenuKind.FOOD,
                List.of("castagne")
        );

        // Vino condiviso tra 2025 e 2026
        UUID vino2026Id = UUID.randomUUID();
        UUID vino2025Id = UUID.randomUUID();
        MenuItemEntity vino2026 = new MenuItemEntity(
                vino2026Id,
                event2026,
                vino2026Id,
                "Vino rosso novello",
                "Novello 2026",
                "New wine 2026",
                100,
                MenuKind.DRINK,
                List.of("vino")
        );
        MenuItemEntity vino2025 = new MenuItemEntity(
                vino2025Id,
                event2025,
                vino2026Id,
                "Vino rosso novello",
                "Novello 2025",
                "New wine 2025",
                0,
                MenuKind.DRINK,
                List.of("vino")
        );

        // Piatto esclusivo del 2025
        UUID polenta2025Id = UUID.randomUUID();
        MenuItemEntity polenta2025 = new MenuItemEntity(
                polenta2025Id,
                event2025,
                polenta2025Id,
                "Polenta con spuntature",
                "Speciale 2025",
                "2025 Special",
                0,
                MenuKind.FOOD,
                List.of("polenta")
        );

        ledZeppelin.addMenuItem(castagne2026);
        ledZeppelin.addMenuItem(vino2026);
        ledZeppelin.addMenuItem(castagne2025);
        ledZeppelin.addMenuItem(vino2025);
        ledZeppelin.addMenuItem(polenta2025);

        when(repository.findAllByOrderByNumberAsc()).thenReturn(List.of(ledZeppelin));
        when(repository.findById(ledZeppelin.getId())).thenReturn(Optional.of(ledZeppelin));

        // 1. Consultazione per edizione: 2026 restituisce solo i 2 item del 2026, 2025 restituisce i 3 item del 2025,
        //    2024 non include la cantina se includeNonParticipating=false
        StandEntity stand2026 = service.findById(ledZeppelin.getId(), event2026);
        assertThat(stand2026.getDescriptionIt()).isEqualTo("Descrizione 2026");
        assertThat(stand2026.getMenuItems()).extracting(MenuItemEntity::getName)
                .containsExactlyInAnyOrder("Castagne arrostite", "Vino rosso novello");

        StandEntity stand2025 = service.findById(ledZeppelin.getId(), event2025);
        assertThat(stand2025.getDescriptionIt()).isEqualTo("Edizione 2025 Decennale");
        assertThat(stand2025.getMenuItems()).extracting(MenuItemEntity::getName)
                .containsExactlyInAnyOrder("Castagne arrostite", "Vino rosso novello", "Polenta con spuntature");

        assertThat(service.findAll(event2024, false)).isEmpty();
        assertThat(service.findAll(event2024, true)).hasSize(1);

        // 2. Continuità delle recensioni tra edizioni per lo stesso piatto/vino:
        //    interrogando le recensioni per castagne2026Id (o castagne2025Id), vengono aggregate entrambe!
        FestivalReviewEntity rev2025 = new FestivalReviewEntity(
                UUID.randomUUID(),
                ReviewTargetType.MENU_ITEM,
                castagne2025Id,
                ledZeppelin.getId(),
                "Castagne arrostite",
                "u-2025",
                "Marco_2025",
                5,
                "Ottime nel 2025!",
                ReviewStatus.APPROVED,
                Instant.now().minusSeconds(86400)
        );
        FestivalReviewEntity rev2026 = new FestivalReviewEntity(
                UUID.randomUUID(),
                ReviewTargetType.MENU_ITEM,
                castagne2026Id,
                ledZeppelin.getId(),
                "Castagne arrostite",
                "u-2026",
                "Elena_2026",
                5,
                "Ottime anche nel 2026!",
                ReviewStatus.APPROVED,
                Instant.now()
        );
        when(reviewRepository.findFilteredByTargetIds(
                org.mockito.ArgumentMatchers.eq(ReviewTargetType.MENU_ITEM),
                org.mockito.ArgumentMatchers.argThat(ids -> ids != null && ids.containsAll(List.of(castagne2026Id, castagne2025Id))),
                org.mockito.ArgumentMatchers.eq(ReviewStatus.APPROVED)
        )).thenReturn(List.of(rev2026, rev2025));

        List<FestivalReviewEntity> castagneReviewsFrom2026 =
                service.listReviews(ReviewTargetType.MENU_ITEM, castagne2026Id, ReviewStatus.APPROVED);
        assertThat(castagneReviewsFrom2026).hasSize(2);

        List<FestivalReviewEntity> castagneReviewsFrom2025 =
                service.listReviews(ReviewTargetType.MENU_ITEM, castagne2025Id, ReviewStatus.APPROVED);
        assertThat(castagneReviewsFrom2025).hasSize(2);
    }
}



