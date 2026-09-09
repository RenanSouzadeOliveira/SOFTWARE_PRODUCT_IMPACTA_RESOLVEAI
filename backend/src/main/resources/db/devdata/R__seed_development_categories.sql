INSERT INTO categorias (nome, descricao)
VALUES
    ('Acesso e autenticação', 'Problemas de acesso, senha ou autenticação.'),
    ('Equipamentos', 'Problemas relacionados a computadores e periféricos.'),
    ('Sistemas acadêmicos', 'Problemas em portais e sistemas acadêmicos.')
ON CONFLICT (nome) DO NOTHING;
