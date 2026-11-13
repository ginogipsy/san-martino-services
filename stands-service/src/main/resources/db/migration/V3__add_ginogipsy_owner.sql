-- Associa l'utente ginogipsy alla cantina #1 (Led Zeppelin) per consentirne la gestione come ROLE_STAND_OWNER
INSERT INTO stand_owners (id, stand_id, first_name, last_name, email, role)
SELECT '33333333-3333-3333-3333-000000010099',
       '22222222-2222-2222-2222-000000000001',
       'ginogipsy',
       'ginogipsy',
       'ginogipsy@gmail.com',
       'SECONDARY' WHERE EXISTS (
    SELECT 1 FROM stands WHERE id = '22222222-2222-2222-2222-000000000001'
)
AND NOT EXISTS (
    SELECT 1 FROM stand_owners WHERE id = '33333333-3333-3333-3333-000000010099'
);
