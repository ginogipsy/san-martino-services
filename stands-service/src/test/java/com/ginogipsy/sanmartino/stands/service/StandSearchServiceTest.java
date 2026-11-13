package com.ginogipsy.sanmartino.stands.service;

import com.ginogipsy.sanmartino.stands.domain.MenuItemEntity;
import com.ginogipsy.sanmartino.stands.domain.MenuKind;
import com.ginogipsy.sanmartino.stands.domain.StandEntity;
import com.ginogipsy.sanmartino.stands.domain.StandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandSearchServiceTest {

    @Mock
    private StandRepository repository;

    private StandService service;

    @BeforeEach
    void setUp() {
        service = new StandService(repository);
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
            item.setId(UUID.randomUUID());
            s.addMenuItem(item);
        }
        return s;
    }
}
