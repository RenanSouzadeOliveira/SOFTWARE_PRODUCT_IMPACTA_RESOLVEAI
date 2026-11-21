ALTER TABLE usuarios ADD COLUMN versao_seguranca BIGINT NOT NULL DEFAULT 0;

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_versao_seguranca CHECK (versao_seguranca >= 0);
