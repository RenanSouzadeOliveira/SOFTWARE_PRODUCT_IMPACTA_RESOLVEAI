-- Ambiente PostgreSQL descartável com Flyway V7 aplicado.
-- psql "$URL_DO_BANCO_DESCARTAVEL" -v ON_ERROR_STOP=1 -f scripts/ac3-query-plans.sql
-- Dados fictícios; a transação é revertida ao final.
BEGIN;
CREATE TEMP TABLE ac3_people AS
WITH inserted AS (
    INSERT INTO usuarios (nome, email, senha_hash, perfil)
    SELECT 'Pessoa fictícia AC3', 'ac3-' || txid_current() || '-' || n || '@example.invalid',
           'hash-ficticio-sem-login', CASE WHEN n <= 50 THEN 'SOLICITANTE' ELSE 'ATENDENTE' END
    FROM generate_series(1, 60) n RETURNING id, perfil
) SELECT id, perfil, row_number() OVER (PARTITION BY perfil ORDER BY id) AS numero FROM inserted;
CREATE TEMP TABLE ac3_category AS
WITH inserted AS (INSERT INTO categorias (nome) VALUES ('Categoria fictícia AC3 ' || txid_current()) RETURNING id)
SELECT id FROM inserted;
INSERT INTO chamados (protocolo, titulo, descricao, status, prioridade, solicitante_id, atendente_id, categoria_id, criado_em)
SELECT 'AC3-' || txid_current() || '-' || n, 'Chamado fictício AC3 ' || n,
       'Descrição fictícia para avaliar planos de consulta',
       CASE WHEN n % 3 = 0 THEN 'EM_ATENDIMENTO' ELSE 'ABERTO' END,
       CASE WHEN n % 2 = 0 THEN 'ALTA' ELSE 'MEDIA' END,
       s.id, CASE WHEN n % 3 = 0 THEN a.id ELSE NULL END, c.id,
       '2026-01-01'::timestamptz + (n % 5) * interval '1 second'
FROM generate_series(1, 50000) n
JOIN ac3_people s ON s.perfil = 'SOLICITANTE' AND s.numero = 1 + n % 50
JOIN ac3_people a ON a.perfil = 'ATENDENTE' AND a.numero = 1 + n % 10
CROSS JOIN ac3_category c;
ANALYZE chamados;
SELECT id AS requester FROM ac3_people WHERE perfil = 'SOLICITANTE' ORDER BY id LIMIT 1 \gset
SELECT id AS assignee FROM ac3_people WHERE perfil = 'ATENDENTE' ORDER BY id LIMIT 1 \gset
SELECT id AS category FROM ac3_category \gset
EXPLAIN (ANALYZE, BUFFERS) SELECT id, criado_em FROM chamados
WHERE solicitante_id = :requester ORDER BY criado_em DESC, id DESC OFFSET 20 LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT id, criado_em FROM chamados
WHERE status = 'ABERTO' AND atendente_id IS NULL ORDER BY criado_em DESC, id DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT id, criado_em FROM chamados
WHERE atendente_id = :assignee ORDER BY criado_em DESC, id DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT id, criado_em FROM chamados
WHERE status = 'ABERTO' AND atendente_id IS NULL AND prioridade = 'ALTA' AND categoria_id = :category
ORDER BY criado_em DESC, id DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM chamados
WHERE atendente_id = :assignee AND status = 'EM_ATENDIMENTO';
ROLLBACK;
