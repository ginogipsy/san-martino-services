-- Seed con le edizioni storiche e prossima della festa di Arce.
-- Dati allineati col MockRepository dell'app Android (le date sono indicative finche'
-- non confermate dall'organizzazione viviAMOarce).

INSERT INTO events (id, name, place, start_date, end_date, description_it, description_en) VALUES
(
    '11111111-1111-1111-1111-000000002026',
    'Le cantine di San Martino 2026',
    'Arce (FR) — centro storico',
    DATE '2026-11-14',
    DATE '2026-11-15',
    'Edizione 2026 della tradizionale festa di San Martino: il centro storico di Arce si trasforma in un percorso di cantine dove ogni mosto diventa vino. Apertura sabato dalle 18:00, domenica dalle 11:00. Data indicativa, in attesa di conferma ufficiale.',
    '2026 edition of the traditional San Martino festival: the historic centre of Arce turns into a trail of wine stands, where every must becomes wine. Saturday from 18:00, Sunday from 11:00. Tentative date, pending official confirmation.'
),
(
    '11111111-1111-1111-1111-000000002025',
    'Le cantine di San Martino 2025',
    'Arce (FR) — centro storico',
    DATE '2025-11-08',
    DATE '2025-11-09',
    'Sabato 8 e domenica 9 novembre 2025. In caso di maltempo la manifestazione e'' stata rinviata al 15 e 16 novembre. Apertura cantine sabato ore 18:00, domenica ore 11:00.',
    'Saturday 8 and Sunday 9 November 2025. In case of bad weather the event was postponed to November 15 and 16. Stands open Saturday at 18:00 and Sunday at 11:00.'
),
(
    '11111111-1111-1111-1111-000000002024',
    'Le cantine di San Martino 2024',
    'Arce (FR) — centro storico',
    DATE '2024-11-09',
    DATE '2024-11-10',
    'Sabato 9 e domenica 10 novembre 2024 (data di recupero per maltempo: 16-17 novembre). Edizione con percorso completo per le vie del centro storico.',
    'Saturday 9 and Sunday 10 November 2024 (bad-weather rescheduling: 16-17 November). Full trail edition through the streets of the old town.'
),
(
    '11111111-1111-1111-1111-000000002018',
    'Le cantine di San Martino 2018',
    'Arce (FR) — centro storico',
    DATE '2018-11-10',
    DATE '2018-11-11',
    'Edizione storica con 58 cantine sparse per il centro storico di Arce. Programma: sabato 10 novembre ore 19:00 apertura cantine; domenica 11 novembre ore 12:00 apertura Fiera Mostra dell''Artigianato.',
    'Historic edition with 58 stands all over the old town of Arce. Programme: Saturday 10 November 6:00 PM stands open; Sunday 11 November noon, Crafts Fair opening.'
),
(
    '11111111-1111-1111-1111-000000002014',
    'Le cantine di San Martino 2014',
    'Arce (FR) — centro storico',
    DATE '2014-11-08',
    DATE '2014-11-09',
    'Edizione 8-9 novembre 2014. Apertura cantine: sabato dalle 18:00, domenica dalle 11:00. Servizio navetta gratuito attivo per tutta la durata della festa.',
    'Edition of November 8-9, 2014. Stands open Saturday from 18:00 and Sunday from 11:00. Free shuttle service throughout the festival.'
);
