-- Rename password_hash -> password (request: kolom cukup 'password', hash Argon2 tetap disimpan)
ALTER TABLE users RENAME COLUMN password_hash TO password;
