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
}
