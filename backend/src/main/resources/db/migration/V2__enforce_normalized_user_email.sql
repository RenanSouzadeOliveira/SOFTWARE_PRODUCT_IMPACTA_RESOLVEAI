UPDATE usuarios
SET email = LOWER(TRIM(email))
WHERE email <> LOWER(TRIM(email));

ALTER TABLE usuarios
    ADD CONSTRAINT ck_usuarios_email_normalizado
    CHECK (email = LOWER(TRIM(email)));
