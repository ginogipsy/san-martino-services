-- =============================================================================
-- V8: Seed Demo Completo per Test Pulsantiera 10 Slot, Ruoli Staff per Postazione,
--     Coda Ordini Live, Recensioni e Analytics
-- =============================================================================

-- 1. Aggiunge francesco-grossi come proprietario della Cantina #1 (Led Zeppelin)
INSERT INTO stand_owners (id, stand_id, first_name, last_name, email, role)
SELECT '33333333-3333-3333-3333-000000010098',
       '22222222-2222-2222-2222-000000000001',
       'francesco-grossi',
       'Grossi',
       'francesco.grossi@sanmartino.local',
       'PRIMARY'
WHERE EXISTS (SELECT 1 FROM stands WHERE id = '22222222-2222-2222-2222-000000000001')
  AND NOT EXISTS (SELECT 1 FROM stand_owners WHERE id = '33333333-3333-3333-3333-000000010098');

-- 2. Completa il menu della Cantina #1 fino a 10 voci (6 FOOD + 4 DRINK) per sfruttare tutti i 10 slot
INSERT INTO menu_items (id, stand_id, name, description_it, description_en, available_plates, kind)
VALUES ('44444444-4444-4444-4444-000000010104', '22222222-2222-2222-2222-000000000001',
        'Zuppa di ceci e castagne',
        'Zuppa calda contadina con ceci locali, castagne e rosmarino.',
        'Warm rustic soup with local chickpeas, chestnuts and rosemary.', 45, 'FOOD'),
       ('44444444-4444-4444-4444-000000010105', '22222222-2222-2222-2222-000000000001',
        'Salsiccia alla brace',
        'Salsiccia paesana cotta alla brace viva, servita con pane casereccio.',
        'Local village sausage grilled over live embers with homemade bread.', 55, 'FOOD'),
       ('44444444-4444-4444-4444-000000010106', '22222222-2222-2222-2222-000000000001',
        'Bruschetta lardo e miele di castagno',
        'Pane bruscato con lardo stagionato, miele di castagno e granella di noci.',
        'Toasted bread with cured lard, chestnut honey and crushed walnuts.', 50, 'FOOD'),
       ('44444444-4444-4444-4444-000000010107', '22222222-2222-2222-2222-000000000001',
        'Tronchetto dolce alle castagne',
        'Dolce tradizionale farcito con crema di castagne e cioccolato fondente.',
        'Traditional roll cake filled with chestnut cream and dark chocolate.', 35, 'FOOD'),
       ('44444444-4444-4444-4444-000000010108', '22222222-2222-2222-2222-000000000001',
        'Cesanese del Piglio DOCG',
        'Calice di vino rosso strutturato del territorio ciociaro.',
        'Glass of full-bodied Ciociaria red wine.', 65, 'DRINK'),
       ('44444444-4444-4444-4444-000000010109', '22222222-2222-2222-2222-000000000001',
        'Vin brule speziato',
        'Vino rosso caldo aromatizzato con cannella, chiodi di garofano e scorza d''arancia.',
        'Hot mulled red wine spiced with cinnamon, cloves and orange peel.', 70, 'DRINK'),
       ('44444444-4444-4444-4444-000000010110', '22222222-2222-2222-2222-000000000001',
        'Liquore artigianale alla castagna',
        'Bicchierino digestivo artigianale infuso alle castagne.',
        'Artisanal chestnut digestive liqueur shot.', 50, 'DRINK')
ON CONFLICT (id) DO NOTHING;

INSERT INTO menu_item_keywords (menu_item_id, keyword)
VALUES ('44444444-4444-4444-4444-000000010104', 'zuppa'),
       ('44444444-4444-4444-4444-000000010104', 'ceci'),
       ('44444444-4444-4444-4444-000000010104', 'castagne'),
       ('44444444-4444-4444-4444-000000010105', 'salsiccia'),
       ('44444444-4444-4444-4444-000000010105', 'brace'),
       ('44444444-4444-4444-4444-000000010105', 'carne'),
       ('44444444-4444-4444-4444-000000010106', 'bruschetta'),
       ('44444444-4444-4444-4444-000000010106', 'miele'),
       ('44444444-4444-4444-4444-000000010106', 'castagne'),
       ('44444444-4444-4444-4444-000000010107', 'dolce'),
       ('44444444-4444-4444-4444-000000010107', 'cioccolato'),
       ('44444444-4444-4444-4444-000000010107', 'castagne'),
       ('44444444-4444-4444-4444-000000010108', 'vino'),
       ('44444444-4444-4444-4444-000000010108', 'cesanese'),
       ('44444444-4444-4444-4444-000000010108', 'rosso'),
       ('44444444-4444-4444-4444-000000010109', 'vino'),
       ('44444444-4444-4444-4444-000000010109', 'brule'),
       ('44444444-4444-4444-4444-000000010109', 'caldo'),
       ('44444444-4444-4444-4444-000000010110', 'liquore'),
       ('44444444-4444-4444-4444-000000010110', 'amaro'),
       ('44444444-4444-4444-4444-000000010110', 'castagne')
ON CONFLICT DO NOTHING;

-- 3. Configurazione Pulsantiera ESP32 per Cantina #1 e Cantina #2
INSERT INTO stand_keypad_configs (stand_id, device_token, next_order_number, last_cashier_seen_at, last_kitchen_seen_at,
                                  last_delivery_seen_at, updated_at)
VALUES ('22222222-2222-2222-2222-000000000001',
        'ESP32-CANTINA-01-DEMO2026',
        4,
        NOW() - INTERVAL '20 seconds',
        NOW() - INTERVAL '15 seconds',
        NOW() - INTERVAL '10 seconds',
        NOW()),
       ('22222222-2222-2222-2222-000000000002',
        'ESP32-CANTINA-02-DEMO2026',
        2,
        NULL,
        NULL,
        NULL,
        NOW())
ON CONFLICT (stand_id) DO NOTHING;

-- 4. Mappatura completa dei 10 Slot per Cantina #1 (Slot 1..6 Piatti Cucina, Slot 7..10 Vini/Bevande)
INSERT INTO stand_keypad_slots (id, stand_id, slot_number, menu_item_id, updated_at)
VALUES ('55555555-5555-5555-5555-000000010001', '22222222-2222-2222-2222-000000000001', 1,
        '44444444-4444-4444-4444-000000010101', NOW()),
       ('55555555-5555-5555-5555-000000010002', '22222222-2222-2222-2222-000000000001', 2,
        '44444444-4444-4444-4444-000000010102', NOW()),
       ('55555555-5555-5555-5555-000000010003', '22222222-2222-2222-2222-000000000001', 3,
        '44444444-4444-4444-4444-000000010104', NOW()),
       ('55555555-5555-5555-5555-000000010004', '22222222-2222-2222-2222-000000000001', 4,
        '44444444-4444-4444-4444-000000010105', NOW()),
       ('55555555-5555-5555-5555-000000010005', '22222222-2222-2222-2222-000000000001', 5,
        '44444444-4444-4444-4444-000000010106', NOW()),
       ('55555555-5555-5555-5555-000000010006', '22222222-2222-2222-2222-000000000001', 6,
        '44444444-4444-4444-4444-000000010107', NOW()),
       ('55555555-5555-5555-5555-000000010007', '22222222-2222-2222-2222-000000000001', 7,
        '44444444-4444-4444-4444-000000010103', NOW()),
       ('55555555-5555-5555-5555-000000010008', '22222222-2222-2222-2222-000000000001', 8,
        '44444444-4444-4444-4444-000000010108', NOW()),
       ('55555555-5555-5555-5555-000000010009', '22222222-2222-2222-2222-000000000001', 9,
        '44444444-4444-4444-4444-000000010109', NOW()),
       ('55555555-5555-5555-5555-000000010010', '22222222-2222-2222-2222-000000000001', 10,
        '44444444-4444-4444-4444-000000010110', NOW())
ON CONFLICT (stand_id, slot_number) DO NOTHING;

-- 5. Permessi granulari Staff per Cantina #1:
--    francesco-grossi (OWNER), ginogipsy (OWNER), maria-chiara (CASHIER),
--    cuoco-mario (KITCHEN), scricci (DELIVERY), lisa-la (VIEWER)
INSERT INTO stand_staff_permissions (id, stand_id, username, permission, granted_by, created_at)
VALUES ('66666666-6666-6666-6666-000000010001', '22222222-2222-2222-2222-000000000001', 'francesco-grossi', 'OWNER',
        'system-seed', NOW()),
       ('66666666-6666-6666-6666-000000010002', '22222222-2222-2222-2222-000000000001', 'ginogipsy', 'OWNER',
        'system-seed', NOW()),
       ('66666666-6666-6666-6666-000000010003', '22222222-2222-2222-2222-000000000001', 'maria-chiara', 'CASHIER',
        'francesco-grossi', NOW()),
       ('66666666-6666-6666-6666-000000010004', '22222222-2222-2222-2222-000000000001', 'cuoco-mario', 'KITCHEN',
        'francesco-grossi', NOW()),
       ('66666666-6666-6666-6666-000000010005', '22222222-2222-2222-2222-000000000001', 'scricci', 'DELIVERY',
        'francesco-grossi', NOW()),
       ('66666666-6666-6666-6666-000000010006', '22222222-2222-2222-2222-000000000001', 'lisa-la', 'VIEWER',
        'francesco-grossi', NOW())
ON CONFLICT (stand_id, username, permission) DO NOTHING;

-- 6. Ordini Demo per Cantina #1:
--    - Ordine #1: DELIVERED (completato)
--    - Ordine #2: IN_PROGRESS (ordine corrente in uscita sul display Consegna #0002:
--                 2x Castagne arrostite [1 pronta, 1 in cottura] + 1x Salsiccia [in cottura] + 1x Vino novello [pronto])
--    - Ordine #3: IN_PROGRESS (in coda dietro l'ordine #2)
INSERT INTO stand_orders (id, stand_id, order_number, status, created_by, created_at, ready_at, delivered_at)
VALUES ('77777777-7777-7777-7777-000000010001',
        '22222222-2222-2222-2222-000000000001',
        1,
        'DELIVERED',
        'maria-chiara',
        NOW() - INTERVAL '18 minutes',
        NOW() - INTERVAL '14 minutes',
        NOW() - INTERVAL '12 minutes'),
       ('77777777-7777-7777-7777-000000010002',
        '22222222-2222-2222-2222-000000000001',
        2,
        'IN_PROGRESS',
        'maria-chiara',
        NOW() - INTERVAL '5 minutes',
        NULL,
        NULL),
       ('77777777-7777-7777-7777-000000010003',
        '22222222-2222-2222-2222-000000000001',
        3,
        'IN_PROGRESS',
        'maria-chiara',
        NOW() - INTERVAL '2 minutes',
        NULL,
        NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO stand_order_items (id, order_id, slot_number, menu_item_id, menu_item_name, kind, ordered_quantity,
                               cooked_quantity, delivered_quantity)
VALUES ('78888888-8888-8888-8888-000000010001',
        '77777777-7777-7777-7777-000000010001',
        1,
        '44444444-4444-4444-4444-000000010101',
        'Castagne arrostite',
        'FOOD',
        2, 2, 2),
       ('78888888-8888-8888-8888-000000010002',
        '77777777-7777-7777-7777-000000010001',
        7,
        '44444444-4444-4444-4444-000000010103',
        'Vino rosso novello',
        'DRINK',
        2, 2, 2),
       ('78888888-8888-8888-8888-000000010003',
        '77777777-7777-7777-7777-000000010002',
        1,
        '44444444-4444-4444-4444-000000010101',
        'Castagne arrostite',
        'FOOD',
        2, 1, 0),
       ('78888888-8888-8888-8888-000000010004',
        '77777777-7777-7777-7777-000000010002',
        4,
        '44444444-4444-4444-4444-000000010105',
        'Salsiccia alla brace',
        'FOOD',
        1, 0, 0),
       ('78888888-8888-8888-8888-000000010005',
        '77777777-7777-7777-7777-000000010002',
        7,
        '44444444-4444-4444-4444-000000010103',
        'Vino rosso novello',
        'DRINK',
        1, 1, 0),
       ('78888888-8888-8888-8888-000000010006',
        '77777777-7777-7777-7777-000000010003',
        3,
        '44444444-4444-4444-4444-000000010104',
        'Zuppa di ceci e castagne',
        'FOOD',
        1, 0, 0),
       ('78888888-8888-8888-8888-000000010007',
        '77777777-7777-7777-7777-000000010003',
        8,
        '44444444-4444-4444-4444-000000010108',
        'Cesanese del Piglio DOCG',
        'DRINK',
        2, 2, 0)
ON CONFLICT (id) DO NOTHING;

-- 7. Recensioni Demo (APPROVED + PENDING per testare moderazione e boost punteggio ricerca)
INSERT INTO festival_reviews (id, target_type, target_id, stand_id, target_name, author_user_id, author_username,
                              rating, comment, status, moderated_by, moderation_note, moderated_at, created_at,
                              updated_at)
VALUES ('88888888-8888-8888-8888-000000010001',
        'STAND',
        '22222222-2222-2222-2222-000000000001',
        '22222222-2222-2222-2222-000000000001',
        'Led Zeppelin',
        'demo-user-1',
        'maria-chiara',
        5,
        'Atmosfera fantastica, castagne cotte alla perfezione e fila scorrevole grazie al tabellone ordini!',
        'APPROVED',
        'francesco-grossi',
        'Recensione verificata',
        NOW() - INTERVAL '1 hour',
        NOW() - INTERVAL '2 hours',
        NOW() - INTERVAL '1 hour'),
       ('88888888-8888-8888-8888-000000010002',
        'MENU_ITEM',
        '44444444-4444-4444-4444-000000010101',
        '22222222-2222-2222-2222-000000000001',
        'Castagne arrostite',
        'demo-user-2',
        'scricci',
        5,
        'Calde, saporite e facilissime da sbucciare. Le migliori della festa!',
        'APPROVED',
        'francesco-grossi',
        'Approvata',
        NOW() - INTERVAL '45 minutes',
        NOW() - INTERVAL '90 minutes',
        NOW() - INTERVAL '45 minutes'),
       ('88888888-8888-8888-8888-000000010003',
        'MENU_ITEM',
        '44444444-4444-4444-4444-000000010104',
        '22222222-2222-2222-2222-000000000001',
        'Zuppa di ceci e castagne',
        'demo-user-3',
        'lisa-la',
        5,
        'Zuppa cremosissima e profumata al rosmarino, perfetta per la serata autunnale.',
        'PENDING',
        NULL,
        NULL,
        NULL,
        NOW() - INTERVAL '20 minutes',
        NOW() - INTERVAL '20 minutes')
ON CONFLICT (id) DO NOTHING;

-- 8. Analytics Demo (Ricerche e Click per popolare la dashboard Statistiche & Ricerche)
INSERT INTO stand_search_logs (id, raw_query, normalized_query, kind_filter, only_available, results_count, created_at)
VALUES ('99999999-9999-9999-9999-000000010001', 'castagne alla brace', 'castagne brace', 'FOOD', TRUE, 2,
        NOW() - INTERVAL '40 minutes'),
       ('99999999-9999-9999-9999-000000010002', 'vino novello', 'vino novello', 'DRINK', TRUE, 4,
        NOW() - INTERVAL '35 minutes'),
       ('99999999-9999-9999-9999-000000010003', 'zuppa ceci', 'zuppa ceci', 'FOOD', FALSE, 1,
        NOW() - INTERVAL '25 minutes'),
       ('99999999-9999-9999-9999-000000010004', 'salsiccia', 'salsiccia', 'FOOD', TRUE, 3,
        NOW() - INTERVAL '15 minutes'),
       ('99999999-9999-9999-9999-000000010005', 'polenta tartufo', 'polenta tartufo', 'FOOD', FALSE, 0,
        NOW() - INTERVAL '10 minutes')
ON CONFLICT (id) DO NOTHING;

INSERT INTO stand_interaction_events (id, stand_id, menu_item_id, interaction_type, search_query, created_at)
VALUES ('99999999-9999-9999-9999-000000020001', '22222222-2222-2222-2222-000000000001',
        '44444444-4444-4444-4444-000000010101', 'MENU_ITEM_CLICK', 'castagne', NOW() - INTERVAL '38 minutes'),
       ('99999999-9999-9999-9999-000000020002', '22222222-2222-2222-2222-000000000001',
        '44444444-4444-4444-4444-000000010101', 'MENU_ITEM_CLICK', 'castagne', NOW() - INTERVAL '30 minutes'),
       ('99999999-9999-9999-9999-000000020003', '22222222-2222-2222-2222-000000000001',
        '44444444-4444-4444-4444-000000010104', 'MENU_ITEM_CLICK', 'zuppa', NOW() - INTERVAL '22 minutes'),
       ('99999999-9999-9999-9999-000000020004', '22222222-2222-2222-2222-000000000001', NULL, 'STAND_FOOD_OPEN', NULL,
        NOW() - INTERVAL '20 minutes'),
       ('99999999-9999-9999-9999-000000020005', '22222222-2222-2222-2222-000000000001', NULL, 'STAND_MAP_OPEN', NULL,
        NOW() - INTERVAL '12 minutes')
ON CONFLICT (id) DO NOTHING;
