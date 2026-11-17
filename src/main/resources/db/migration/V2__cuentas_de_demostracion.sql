-- Cuentas de demostración: una marca para recortar sus permisos y reiniciar sus datos cada noche.
ALTER TABLE users ADD COLUMN demo BOOLEAN DEFAULT FALSE NOT NULL;

-- Las cuentas de demostración pasan del dominio anterior (@bibliospace.local) al de Espacia,
-- salvo que ya exista una cuenta con el correo nuevo.
UPDATE users
SET email = REPLACE(email, '@bibliospace.local', '@espacia.local')
WHERE email IN ('estudiante@bibliospace.local', 'julian.gomez@bibliospace.local', 'funcionario@bibliospace.local')
  AND NOT EXISTS (SELECT 1 FROM users otra WHERE otra.email = REPLACE(users.email, '@bibliospace.local', '@espacia.local'));

UPDATE users
SET demo = TRUE
WHERE email IN ('estudiante@espacia.local', 'julian.gomez@espacia.local', 'funcionario@espacia.local');
