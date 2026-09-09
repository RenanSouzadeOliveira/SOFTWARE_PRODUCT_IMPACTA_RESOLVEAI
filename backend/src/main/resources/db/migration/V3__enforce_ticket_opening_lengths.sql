ALTER TABLE chamados
    ALTER COLUMN titulo TYPE VARCHAR(120);

ALTER TABLE chamados
    ALTER COLUMN descricao TYPE VARCHAR(2000);

ALTER TABLE chamados
    DROP CONSTRAINT ck_chamados_titulo_nao_vazio;

ALTER TABLE chamados
    DROP CONSTRAINT ck_chamados_descricao_nao_vazia;

ALTER TABLE chamados
    ADD CONSTRAINT ck_chamados_titulo_tamanho
    CHECK (CHAR_LENGTH(TRIM(titulo)) BETWEEN 5 AND 120);

ALTER TABLE chamados
    ADD CONSTRAINT ck_chamados_descricao_tamanho
    CHECK (CHAR_LENGTH(TRIM(descricao)) BETWEEN 20 AND 2000);

-- V1 ja fornece uk_chamados_protocolo e os indices cujas primeiras colunas sao
-- solicitante_id, status e categoria_id; nenhum indice redundante e criado aqui.
