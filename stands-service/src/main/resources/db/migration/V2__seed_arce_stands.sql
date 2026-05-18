-- Seed di alcune cantine rappresentative dalle 49 storiche di Arce.
-- I dati sono allineati con il MockRepository dell'app Android. La V3 (futura)
-- completera' fino a 49 cantine. Nomi/anno/coordinate da verificare con
-- l'organizzazione viviAMOarce.

-- 1. Led Zeppelin (n.1) -- castagne
INSERT INTO stands (id, number, name, description_it, description_en, first_participation_year, latitude, longitude) VALUES
('22222222-2222-2222-2222-000000000001', 1, 'Led Zeppelin',
 'Cantina specializzata nelle castagne, cuore della tradizione autunnale.',
 'Stand specialised in chestnuts, the heart of the autumn tradition.',
 2014, 41.5822, 13.5775);

INSERT INTO stand_owners (id, stand_id, first_name, last_name, role) VALUES
('33333333-3333-3333-3333-000000010001', '22222222-2222-2222-2222-000000000001', 'Mario', 'Bianchi', 'PRIMARY');

INSERT INTO menu_items (id, stand_id, name, description_it, description_en, available_plates, kind) VALUES
('44444444-4444-4444-4444-000000010101', '22222222-2222-2222-2222-000000000001',
 'Castagne arrostite',
 'Castagne cotte sulla brace, servite calde.',
 'Chestnuts roasted over embers, served hot.', 80, 'FOOD'),
('44444444-4444-4444-4444-000000010102', '22222222-2222-2222-2222-000000000001',
 'Castagne lesse',
 'Castagne bollite con foglie di alloro.',
 'Boiled chestnuts with bay leaves.', 40, 'FOOD'),
('44444444-4444-4444-4444-000000010103', '22222222-2222-2222-2222-000000000001',
 'Vino rosso novello',
 'Vino novello locale, fresco di mosto.',
 'Local new red wine, freshly fermented.', 100, 'DRINK');

INSERT INTO menu_item_keywords (menu_item_id, keyword) VALUES
('44444444-4444-4444-4444-000000010101', 'castagne'),
('44444444-4444-4444-4444-000000010101', 'brace'),
('44444444-4444-4444-4444-000000010101', 'autunno'),
('44444444-4444-4444-4444-000000010102', 'castagne'),
('44444444-4444-4444-4444-000000010102', 'alloro'),
('44444444-4444-4444-4444-000000010103', 'vino'),
('44444444-4444-4444-4444-000000010103', 'rosso'),
('44444444-4444-4444-4444-000000010103', 'novello');

-- 2. La cantina la campa (n.2) -- pizzelle, cicoria
INSERT INTO stands (id, number, name, description_it, description_en, first_participation_year, latitude, longitude) VALUES
('22222222-2222-2222-2222-000000000002', 2, 'La cantina la campa',
 'Storica cantina del centro: pizzelle, cicoria al cordone e melanzane alla brace.',
 'Historic stand in the old town: pizzelle, cordon chicory and char-grilled aubergines.',
 2014, 41.5820, 13.5779);

INSERT INTO stand_owners (id, stand_id, first_name, last_name, role) VALUES
('33333333-3333-3333-3333-000000020001', '22222222-2222-2222-2222-000000000002', 'Giuseppe', 'Verdi', 'PRIMARY'),
('33333333-3333-3333-3333-000000020002', '22222222-2222-2222-2222-000000000002', 'Anna', 'Verdi', 'SECONDARY');

INSERT INTO menu_items (id, stand_id, name, description_it, description_en, available_plates, kind) VALUES
('44444444-4444-4444-4444-000000020101', '22222222-2222-2222-2222-000000000002',
 'Pizzelle fritte',
 'Piccole pizze fritte servite calde.',
 'Small fried pizza dough, served hot.', 60, 'FOOD'),
('44444444-4444-4444-4444-000000020102', '22222222-2222-2222-2222-000000000002',
 'Cicoria al cordone',
 'Cicoria locale ripassata, ricetta tradizionale.',
 'Local chicory pan-tossed in the traditional way.', 30, 'FOOD'),
('44444444-4444-4444-4444-000000020103', '22222222-2222-2222-2222-000000000002',
 'Vino rosso della casa',
 'Vino rosso prodotto in zona.',
 'Locally produced red wine.', 90, 'DRINK');

INSERT INTO menu_item_keywords (menu_item_id, keyword) VALUES
('44444444-4444-4444-4444-000000020101', 'pizza'),
('44444444-4444-4444-4444-000000020101', 'fritto'),
('44444444-4444-4444-4444-000000020102', 'cicoria'),
('44444444-4444-4444-4444-000000020102', 'verdura'),
('44444444-4444-4444-4444-000000020103', 'vino'),
('44444444-4444-4444-4444-000000020103', 'rosso');

-- 11. Pro Loco Roccadarce (n.11) -- trippetta, salsiccia
INSERT INTO stands (id, number, name, description_it, description_en, first_participation_year, latitude, longitude) VALUES
('22222222-2222-2222-2222-000000000011', 11, 'Pro Loco Roccadarce',
 'Cantina della Pro Loco del paese vicino: trippetta e salsiccia al sugo.',
 'Stand from the Pro Loco of the neighbouring village: tripe and sausage in ragù.',
 2014, 41.5823, 13.5789);

INSERT INTO stand_owners (id, stand_id, first_name, last_name, role) VALUES
('33333333-3333-3333-3333-000000110001', '22222222-2222-2222-2222-000000000011', 'Luigi', 'Esposito', 'PRIMARY');

INSERT INTO menu_items (id, stand_id, name, description_it, description_en, available_plates, kind) VALUES
('44444444-4444-4444-4444-000000110101', '22222222-2222-2222-2222-000000000011',
 'Trippetta al sugo',
 'Trippa cotta lentamente in sugo di pomodoro.',
 'Tripe slow-cooked in tomato sauce.', 25, 'FOOD'),
('44444444-4444-4444-4444-000000110102', '22222222-2222-2222-2222-000000000011',
 'Salsiccia e fagioli al sugo',
 'Salsiccia paesana stufata con fagioli.',
 'Village sausage stewed with beans.', 30, 'FOOD'),
('44444444-4444-4444-4444-000000110103', '22222222-2222-2222-2222-000000000011',
 'Vino rosso novello',
 'Vino novello della Valle del Liri.',
 'New red wine from the Liri Valley.', 80, 'DRINK');

INSERT INTO menu_item_keywords (menu_item_id, keyword) VALUES
('44444444-4444-4444-4444-000000110101', 'trippa'),
('44444444-4444-4444-4444-000000110101', 'sugo'),
('44444444-4444-4444-4444-000000110102', 'salsiccia'),
('44444444-4444-4444-4444-000000110102', 'fagioli'),
('44444444-4444-4444-4444-000000110103', 'vino'),
('44444444-4444-4444-4444-000000110103', 'novello');

-- 34. Nonna Maria (n.34) -- polenta, pecora
INSERT INTO stands (id, number, name, description_it, description_en, first_participation_year, latitude, longitude) VALUES
('22222222-2222-2222-2222-000000000034', 34, 'Nonna Maria',
 'Polenta al sugo, pecora al sugo, sapori della tradizione contadina.',
 'Polenta in ragù, mutton stew, flavours of peasant tradition.',
 2014, 41.5810, 13.5790);

INSERT INTO stand_owners (id, stand_id, first_name, last_name, role) VALUES
('33333333-3333-3333-3333-000000340001', '22222222-2222-2222-2222-000000000034', 'Maria', 'Rossi', 'PRIMARY');

INSERT INTO menu_items (id, stand_id, name, description_it, description_en, available_plates, kind) VALUES
('44444444-4444-4444-4444-000000340101', '22222222-2222-2222-2222-000000000034',
 'Polenta al sugo di pecora',
 'Polenta cotta a fuoco lento con ragù di pecora.',
 'Slow-cooked polenta with mutton ragù.', 30, 'FOOD'),
('44444444-4444-4444-4444-000000340102', '22222222-2222-2222-2222-000000000034',
 'Pecora al sugo',
 'Pecora stufata a lungo nel sugo, ricetta della nonna.',
 'Long-stewed mutton in ragù, grandma''s recipe.', 25, 'FOOD'),
('44444444-4444-4444-4444-000000340103', '22222222-2222-2222-2222-000000000034',
 'Vino rosso della nonna',
 'Vino rosso prodotto in casa.',
 'Home-made red wine.', 50, 'DRINK');

INSERT INTO menu_item_keywords (menu_item_id, keyword) VALUES
('44444444-4444-4444-4444-000000340101', 'polenta'),
('44444444-4444-4444-4444-000000340101', 'pecora'),
('44444444-4444-4444-4444-000000340101', 'sugo'),
('44444444-4444-4444-4444-000000340102', 'pecora'),
('44444444-4444-4444-4444-000000340102', 'sugo'),
('44444444-4444-4444-4444-000000340102', 'carne'),
('44444444-4444-4444-4444-000000340103', 'vino'),
('44444444-4444-4444-4444-000000340103', 'rosso');

-- 43. Il tartufo del Tobia (n.43) -- tartufo
INSERT INTO stands (id, number, name, description_it, description_en, first_participation_year, latitude, longitude) VALUES
('22222222-2222-2222-2222-000000000043', 43, 'Il tartufo del Tobia',
 'Specialità al tartufo: gnocchi, bruschette e salsiccia.',
 'Truffle specialities: gnocchi, bruschetta and sausage.',
 2016, 41.5819, 13.5776);

INSERT INTO stand_owners (id, stand_id, first_name, last_name, role) VALUES
('33333333-3333-3333-3333-000000430001', '22222222-2222-2222-2222-000000000043', 'Tobia', 'Ferrari', 'PRIMARY');

INSERT INTO menu_items (id, stand_id, name, description_it, description_en, available_plates, kind) VALUES
('44444444-4444-4444-4444-000000430101', '22222222-2222-2222-2222-000000000043',
 'Gnocchi al tartufo',
 'Gnocchi di patate conditi con burro e tartufo nero.',
 'Potato gnocchi tossed in butter and black truffle.', 25, 'FOOD'),
('44444444-4444-4444-4444-000000430102', '22222222-2222-2222-2222-000000000043',
 'Bruschette al tartufo',
 'Pane abbrustolito con crema al tartufo.',
 'Toasted bread with truffle cream.', 40, 'FOOD'),
('44444444-4444-4444-4444-000000430103', '22222222-2222-2222-2222-000000000043',
 'Vino rosso riserva',
 'Rosso strutturato della Valle del Liri.',
 'Full-bodied red from the Liri Valley.', 50, 'DRINK');

INSERT INTO menu_item_keywords (menu_item_id, keyword) VALUES
('44444444-4444-4444-4444-000000430101', 'gnocchi'),
('44444444-4444-4444-4444-000000430101', 'tartufo'),
('44444444-4444-4444-4444-000000430101', 'pasta'),
('44444444-4444-4444-4444-000000430102', 'tartufo'),
('44444444-4444-4444-4444-000000430102', 'bruschetta'),
('44444444-4444-4444-4444-000000430102', 'pane'),
('44444444-4444-4444-4444-000000430103', 'vino'),
('44444444-4444-4444-4444-000000430103', 'rosso'),
('44444444-4444-4444-4444-000000430103', 'riserva');
