# AGENT Guidelines — test-crud-app-api-v2

> Stack: Quarkus 3.39.1 + Java 25 + Gradle Kotlin DSL + PostgreSQL 18 (murni, tanpa ORM berat).
> Tujuan: Efisiensi Postgres maksimal, audit konsisten, soft delete, public identifier via UUID v4, DTO immutable via `record`, dan I/O bound via Virtual Thread.

---

## 1. Prinsip Utama (Postgres Only)

1.  **Murni Postgres.** Jangan bungkus Postgres dengan abstraksi yang menyembunyikan SQL. Query harus eksplisit, `EXPLAIN (ANALYZE, BUFFERS)`-friendly. Hindari N+1, hindari `SELECT *`.
2.  **95% I/O Bound → Virtual Thread.** Jangan pakai reactive (`Uni/Multi`) untuk CRUD biasa. Biarkan JDBC blocking berjalan di Virtual Thread.
3.  **DTO = `record`, Entity = `class`.** Public API tidak pernah expose `id`/`fk_id` internal.
4.  **Sumber kebenaran migrasi:** `src/main/resources/db/migration/V__*.sql` via `quarkus-flyway` (atau `quarkus-liquibase` jika dipilih, tapi prefer Flyway untuk SQL murni).
5.  **Semua tabel wajib punya:** PK `BIGINT IDENTITY`, `uuid`, 6 kolom audit, `deleted_at` untuk soft delete.
6.  **Index by design:** `btree` untuk PK/unique/ordering, `hash` untuk `uuid` equality, `GIN` hanya jika butuh JSONB/full-text.

---

## 2. Konvensi Penamaan & Tipe Standar

| Elemen | Aturan |
| :--- | :--- |
| **Tabel** | `snake_case` plural: `users`, `products`, `product_categories`. Jangan pakai `tbl_` prefix. |
| **Kolom** | `snake_case`. PK = `id`, Public = `uuid`, FK = `singular_id` (mis. `category_id`). |
| **PK & FK** | `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY` (standar PG 10+, pengganti `SERIAL`/`BIGSERIAL`). Jangan pakai `SERIAL` (legacy `INT4` limit 2.1M). |
| **FK `*_by`** | `BIGINT NULL` tanpa `FOREIGN KEY` constraint. Sumber `users.id` tapi `NO FK` (hindari lock & dead-lock audit). Validasi di aplikasi. |
| **Waktu** | `TIMESTAMPTZ NOT NULL DEFAULT now()` — jangan `TIMESTAMP WITHOUT TZ`. |
| **UUID** | `UUID NOT NULL DEFAULT gen_random_uuid()` (butuh `pgcrypto`). v4. |
| **Text** | `TEXT` bukan `VARCHAR(255)` kecuali ada constraint panjang bisnis. |
| **Boolean** | `BOOLEAN NOT NULL DEFAULT FALSE`. |

### Extension Wajib

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto; -- untuk gen_random_uuid()
-- opsional jika butuh trigram: CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

---

## 3. Kolom Audit Wajib (6 Kolom di Tiap Tabel)

Setiap tabel **WAJIB** punya 6 kolom ini, urutan konsisten:

```sql
created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
created_by BIGINT NULL,       -- ref users.id, NO FK
updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
updated_by BIGINT NULL,       -- ref users.id, NO FK
deleted_at TIMESTAMPTZ NULL,  -- NULL = aktif, NOT NULL = soft-deleted
deleted_by BIGINT NULL        -- ref users.id, NO FK
```

**Aturan Pengisian:**

*   `created_at`/`created_by`: diisi saat `INSERT`. `created_by` boleh `NULL` hanya untuk seed `users` pertama.
*   `updated_at`/`updated_by`: diisi via `BEFORE UPDATE` trigger **atau** eksplisit di `UPDATE SET updated_at=now(), updated_by=:actor`. Pilih salah satu, konsisten di seluruh codebase. Rekomendasi: **eksplisit di query** (lebih transparan untuk audit).
    ```sql
    -- Trigger alternatif (jika dipilih, dokumentasikan di AGENT.md):
    CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
    BEGIN NEW.updated_at = now(); RETURN NEW; END; $$ LANGUAGE plpgsql;
    CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    ```
*   `deleted_at`/`deleted_by`: hanya diisi saat soft delete. `NULL` jika aktif.

**Jangan:**
*   `created_at` nullable.
*   `*_by` pakai `FOREIGN KEY`.
*   Pakai `DEFAULT now()` untuk `deleted_at`.

---

## 4. Soft Delete (Partial Index untuk Unique)

### 4.1 Query Scope Default

Semua `SELECT`, `UPDATE`, `DELETE`, `JOIN` **WAJIB** filter `deleted_at IS NULL` kecuali eksplisit butuh data terhapus (mis. admin restore).

```sql
-- READ
SELECT ... FROM products WHERE deleted_at IS NULL AND uuid = :uuid;

-- JOIN
SELECT p.uuid, c.uuid AS category_uuid
FROM products p
JOIN categories c ON c.id = p.category_id AND c.deleted_at IS NULL
WHERE p.deleted_at IS NULL;

-- UPDATE (hanya row aktif)
UPDATE products
SET name = :name, updated_at = now(), updated_by = :actor
WHERE uuid = :uuid AND deleted_at IS NULL;

-- SOFT DELETE
UPDATE products
SET deleted_at = now(), deleted_by = :actor
WHERE uuid = :uuid AND deleted_at IS NULL;

-- RESTORE
UPDATE products
SET deleted_at = NULL, deleted_by = NULL, updated_at = now(), updated_by = :actor
WHERE uuid = :uuid AND deleted_at IS NOT NULL;

-- HARD DELETE (hanya via job/cron, tidak dari API publik)
DELETE FROM products WHERE uuid = :uuid AND deleted_at IS NOT NULL;
```

### 4.2 Unique Constraint → Partial Index

**JANGAN** `CREATE UNIQUE INDEX ux_x ON tbl(col)` — akan blokir re-insert setelah soft delete.

**WAJIB** `WHERE deleted_at IS NULL`:

```sql
-- Benar
CREATE UNIQUE INDEX ux_users_email_active ON users(email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_products_sku_active ON products(sku) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_categories_slug_active ON categories(slug) WHERE deleted_at IS NULL;

-- Salah — jangan dipakai
-- CREATE UNIQUE INDEX ux_users_email ON users(email);
```

Keuntungan: index lebih kecil (hanya row aktif), `VACUUM` lebih cepat, re-use `email`/`sku` setelah soft delete tetap bisa.

### 4.3 Index Pendukung Soft Delete

Tambahkan index untuk filter cepat jika tabel besar & banyak soft delete:

```sql
CREATE INDEX ix_products_deleted_at ON products(deleted_at) WHERE deleted_at IS NOT NULL;
-- atau cukup andalkan partial index di atas; jangan double index tanpa ukur via EXPLAIN
```

---

## 5. UUID v4 (Public Identifier)

### 5.1 Definisi

*   Setiap tabel punya `uuid UUID NOT NULL DEFAULT gen_random_uuid()` — v4 random.
*   **Jangan** `UNIQUE` di kolom `uuid` via hash. Hash index di Postgres tidak support `UNIQUE`.
*   Jika butuh guarantee unique untuk integritas, tambah `UNIQUE btree` terpisah — tapi ini duplicate. Rekomendasi guideline ini: **andalkan `gen_random_uuid()` + collision probability astronomically low**, cukup `hash` untuk lookup. Jika audit butuh strict unique, tambah:
    ```sql
    -- opsional, hanya jika butuh DB-level guarantee:
    CREATE UNIQUE INDEX ux_products_uuid_unique ON products(uuid);
    -- query tetap pakai hash index untuk equality
    ```

### 5.2 Hash Index (Non-Unique)

```sql
CREATE INDEX ix_users_uuid_hash ON users USING hash (uuid);
CREATE INDEX ix_products_uuid_hash ON products USING hash (uuid);
CREATE INDEX ix_categories_uuid_hash ON categories USING hash (uuid);
```

*   `USING hash` hanya untuk `WHERE uuid = ?` (equality). Tidak support `ORDER BY`, `>`, `LIKE`.
*   Lebih kecil & cepat 30-50% untuk point lookup vs `btree`. Sudah WAL-logged sejak PG 10.
*   Jangan `CREATE UNIQUE INDEX ... USING hash` — tidak didukung.

### 5.3 Alur FK UUID

Internal tetap `BIGINT` (`id`/`category_id`), eksternal `UUID`:

```sql
-- categories: id BIGINT PK, uuid UUID
-- products: category_id BIGINT NOT NULL
```

---

## 6. DTO Rule — `id`/`fk_id` Tidak Pernah Expose

### 6.1 Prinsip

*   **Entity/Record internal:** `id`, `categoryId` (`BIGINT`).
*   **DTO/record publik:** `uuid`, `categoryUuid` (`UUID`).
*   Client tidak pernah kirim/terima `id` numeric.

### 6.2 Mapping

**Read:**
```sql
SELECT p.uuid,
       p.name,
       p.sku,
       c.uuid AS category_uuid,
       p.created_at,
       p.updated_at
FROM products p
JOIN categories c ON c.id = p.category_id AND c.deleted_at IS NULL
WHERE p.deleted_at IS NULL
  AND p.uuid = :uuid; -- pakai hash index
```

**Write (Create):**
```java
// 1. Resolve fk_uuid -> id (1 query, hash index)
Long categoryId = jdbc.queryForObject(
    "SELECT id FROM categories WHERE uuid = :uuid AND deleted_at IS NULL",
    Map.of("uuid", req.categoryUuid()), Long.class);
// 2. Insert pakai id internal, returning uuid
UUID newUuid = jdbc.queryForObject(
    "INSERT INTO products (category_id, name, sku, created_by) VALUES (:catId, :name, :sku, :actor) RETURNING uuid",
    Map.of("catId", categoryId, "name", req.name(), "sku", req.sku(), "actor", actorId), UUID.class);
```

**Update/Delete:** `WHERE uuid = :uuid AND deleted_at IS NULL`.

### 6.3 Validasi

*   Jika `fk_uuid` tidak ditemukan / sudah soft delete → `404 Not Found` (jangan `500`).
*   Jangan pernah `SELECT id` lalu expose di JSON.

---

## 7. DTO pakai `record` (Java 25)

### 7.1 Aturan

*   Semua `Request`/`Response` DTO **WAJIB** `record` — immutable, compact, auto `equals/hashCode`.
*   Entity/Row Mapper boleh `class` (butuh setter/mutable untuk audit).
*   Validasi pakai `jakarta.validation` di komponen `record`.

```java
package id.my.agungdh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import java.time.Instant;

public record ProductCreateRequest(
    @NotBlank String name,
    @NotBlank String sku,
    @NotNull UUID categoryUuid
) {}

public record ProductUpdateRequest(
    @NotBlank String name,
    @NotNull UUID categoryUuid
) {}

public record ProductResponse(
    UUID uuid,
    String name,
    String sku,
    UUID categoryUuid,
    Instant createdAt,
    Instant updatedAt
) {}

public record PageResponse<T>(
    java.util.List<T> data,
    String nextCursor,
    boolean hasNext
) {}
```

### 7.2 JSON

*   Quarkus `quarkus-rest-jackson` sudah support `record` tanpa config.
*   Jika butuh `snake_case` di JSON: `@JsonProperty("category_uuid")` atau config `quarkus.jackson.property-naming-strategy=SNAKE_CASE`.
*   Jangan pakai `quarkus-rest-jsonb` + `record` tanpa test — Jackson lebih mature untuk record.

### 7.3 Mapper

Gunakan manual mapper atau `MapStruct` dengan `componentModel="cdi"`. Jangan pakai reflection berat.

```java
public static ProductResponse toResponse(ProductRow row, UUID categoryUuid) {
    return new ProductResponse(row.uuid(), row.name(), row.sku(), categoryUuid, row.createdAt(), row.updatedAt());
}
```

---

## 8. Virtual Thread untuk I/O Heavy (95% I/O Bound)

Project ini I/O bound (Postgres, Valkey, Minio).

### 8.1 Aturan

*   **WAJIB** `@RunOnVirtualThread` di semua `Resource` (`@Path`) dan `Service` yang melakukan I/O.
    ```java
    package id.my.agungdh;

    import io.smallrye.common.annotation.RunOnVirtualThread;
    import jakarta.ws.rs.*;
    import jakarta.ws.rs.core.MediaType;

    @Path("/products")
    @RunOnVirtualThread // <-- wajib
    public class ProductResource {
        @GET
        @Path("/{uuid}")
        @Produces(MediaType.APPLICATION_JSON)
        public ProductResponse getByUuid(@PathParam("uuid") UUID uuid) {
            return productService.findByUuid(uuid);
        }
    }
    ```
*   `Service` juga `@RunOnVirtualThread` jika dipanggil tanpa `Resource` (mis. scheduler).
    ```java
    @ApplicationScoped
    @RunOnVirtualThread
    public class ProductService { ... }
    ```
*   Jangan pakai `@Blocking` / `@NonBlocking` manual — VT sudah pin ke carrier thread. Jangan bungkus JDBC dengan `Uni.createFrom().item()` untuk CRUD biasa.
*   Konfigurasi `application.yml` (Agroal butuh pool lebih besar karena VT murah):
    ```yaml
    quarkus:
      datasource:
        db-kind: postgresql
        jdbc:
          url: jdbc:postgresql://localhost:5432/crud
          max-size: 50
        agroal:
          max-size: 50
          min-size: 5
      flyway:
        migrate-at-start: true
        locations: db/migration
    ```

### 8.2 Larangan VT

*   Jangan taruh CPU-bound berat (bcrypt, image resize, crypto) di VT tanpa `Executor` terpisah — akan pin carrier thread.
*   Jangan `synchronized` di VT (akan pin). Gunakan `ReentrantLock`.
*   Jangan `ThreadLocal` berlebihan — VT jumlahnya banyak, bisa OOM.

---

## 9. DDL Template Reusable (Copy-Paste)

```sql
-- V1__init.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ========== users (tabel pertama, seed butuh created_by NULL) ==========
CREATE TABLE users (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid UUID NOT NULL DEFAULT gen_random_uuid(),
  email TEXT NOT NULL,
  name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT NULL,
  deleted_at TIMESTAMPTZ NULL,
  deleted_by BIGINT NULL
);
CREATE INDEX ix_users_uuid_hash ON users USING hash (uuid);
CREATE UNIQUE INDEX ux_users_email_active ON users(email) WHERE deleted_at IS NULL;

-- ========== categories ==========
CREATE TABLE categories (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid UUID NOT NULL DEFAULT gen_random_uuid(),
  slug TEXT NOT NULL,
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT NULL,
  deleted_at TIMESTAMPTZ NULL,
  deleted_by BIGINT NULL
);
CREATE INDEX ix_categories_uuid_hash ON categories USING hash (uuid);
CREATE UNIQUE INDEX ux_categories_slug_active ON categories(slug) WHERE deleted_at IS NULL;

-- ========== products ==========
CREATE TABLE products (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid UUID NOT NULL DEFAULT gen_random_uuid(),
  category_id BIGINT NOT NULL, -- FK internal, no FK constraint optional
  sku TEXT NOT NULL,
  name TEXT NOT NULL,
  description TEXT NULL,
  price_cents BIGINT NOT NULL CHECK (price_cents >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT NULL,
  deleted_at TIMESTAMPTZ NULL,
  deleted_by BIGINT NULL,
  CONSTRAINT fk_products_category_id FOREIGN KEY (category_id) REFERENCES categories(id)
  -- Jika mau murni tanpa FK constraint, hapus line CONSTRAINT di atas
  -- dan validasi di aplikasi. Guideline ini prefer: FK untuk relasi bisnis (category_id),
  -- NO FK hanya untuk kolom audit *_by.
);
CREATE INDEX ix_products_uuid_hash ON products USING hash (uuid);
CREATE UNIQUE INDEX ux_products_sku_active ON products(sku) WHERE deleted_at IS NULL;
CREATE INDEX ix_products_category_id_active ON products(category_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_products_created_at_active ON products(created_at) WHERE deleted_at IS NULL;
```

**Catatan:**
*   FK bisnis (`category_id`) **boleh** pakai `FOREIGN KEY` (beda dengan `*_by` yang dilarang). Jika mau murni tanpa FK sama sekali, hapus constraint.
*   Selalu tambahkan `CHECK` untuk bisnis rule di DB (`price_cents >= 0`).

---

## 10. Query Pattern & Efisiensi Postgres

### 10.1 SELECT

*   Selalu sebut kolom, jangan `SELECT *`.
*   Selalu `WHERE deleted_at IS NULL` (buat helper `whereActive()` di Repository).
*   Gunakan `LIMIT` + `keyset pagination` untuk list, jangan `OFFSET` besar:
    ```sql
    -- Keyset (cursor) — O(log n)
    SELECT uuid, name FROM products
    WHERE deleted_at IS NULL
      AND (created_at, id) > (:cursorCreatedAt, :cursorId)
    ORDER BY created_at ASC, id ASC
    LIMIT :pageSize;

    -- Jangan OFFSET 100000 — O(n)
    -- SELECT ... OFFSET 100000 LIMIT 20;
    ```

### 10.2 INSERT

```sql
INSERT INTO products (category_id, sku, name, created_by)
VALUES (:catId, :sku, :name, :actor)
RETURNING uuid, created_at;
```

### 10.3 UPDATE

```sql
UPDATE products
SET name = :name, category_id = :catId, updated_at = now(), updated_by = :actor
WHERE uuid = :uuid AND deleted_at IS NULL
RETURNING uuid, updated_at;
-- cek rowCount == 0 -> 404
```

### 10.4 Indexing Guide

*   `btree` = default untuk PK, unique, range, `ORDER BY`.
*   `hash` = hanya untuk `uuid = ?`.
*   `GIN` = untuk `JSONB`, `to_tsvector`, atau `ILIKE '%foo%'` dengan `pg_trgm`:
    ```sql
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
    CREATE INDEX ix_products_name_trgm ON products USING gin (name gin_trgm_ops) WHERE deleted_at IS NULL;
    -- query: WHERE name ILIKE '%lap%' AND deleted_at IS NULL
    ```
*   Partial index lebih kecil → `VACUUM` & `ANALYZE` lebih cepat.
*   Jangan buat index tanpa ukur: selalu `EXPLAIN (ANALYZE, BUFFERS)` sebelum & sesudah.

### 10.5 Koneksi & Transaksi

*   `@Transactional` sesingkat mungkin, jangan buka transaksi untuk `GET` read-only jika tidak perlu.
*   Default `READ COMMITTED` cukup. `SELECT ... FOR UPDATE` hanya jika butuh cegah race.
*   Gunakan `try-with-resources` untuk `Connection` jika pakai raw JDBC.

### 10.6 Hal yang Dilarang

*   `SELECT *`
*   `OFFSET` besar (>1000)
*   `OR` tanpa index (`WHERE a=1 OR b=2` → pakai `UNION`)
*   `LIKE '%x%'` tanpa `pg_trgm`
*   `VARCHAR` tanpa length check
*   Trigger yang hidden side-effect (kecuali disepakati)

---

## 11. Struktur Project (Quarkus)

```
src/main/java/id/my/agungdh/
├── dto/           # record Request/Response
├── entity/        # class Row/Entity (internal id)
├── repository/    # JDBC/JOOQ/DB access, whereActive() helper
├── service/       # @RunOnVirtualThread, @ApplicationScoped, resolve uuid->id
├── resource/      # @Path, @RunOnVirtualThread, validasi, return DTO
└── filter/        # Security filter isi actorId untuk audit *_by

src/main/resources/
├── application.yml
└── db/migration/  # V1__init.sql, V2__add_*.sql
```

---

## 12. Checklist Review PR (WAJIB Lolos)

*   [ ] Semua tabel baru punya 6 kolom audit + `uuid` + `hash index`?
*   [ ] Semua `UNIQUE` pakai `WHERE deleted_at IS NULL`?
*   [ ] Kolom `*_by` = `BIGINT NULL` tanpa `FOREIGN KEY`?
*   [ ] Tidak ada `SELECT *` atau `OFFSET` besar?
*   [ ] DTO pakai `record` dan tidak expose `id`/`fk_id`?
*   [ ] Semua `WHERE`/`JOIN` filter `deleted_at IS NULL`?
*   [ ] `uuid` lookup pakai `= :uuid` (hash index terpakai)?
*   [ ] `Resource`/`Service` I/O pakai `@RunOnVirtualThread`?
*   [ ] `EXPLAIN ANALYZE` dilampirkan untuk query baru?
*   [ ] Migrasi `V__*.sql` ada dan `flyway migrate` sukses di local?

---

## 13. Contoh `application.yml` Minimal

```yaml
quarkus:
  datasource:
    db-kind: postgresql
    username: admin
    password: admin
    jdbc:
      url: jdbc:postgresql://localhost:5432/crud
  flyway:
    migrate-at-start: true
    locations: db/migration
  log:
    category:
      "io.quarkus.agroal": DEBUG
      "org.flywaydb": INFO

# Virtual threads default sufficient — no extra config
```

---

## 14. Referensi

*   Postgres 18 Docs: `GENERATED AS IDENTITY`, `CREATE INDEX ... USING hash`, `Partial Indexes`.
*   `src/main/java/id/my/agungdh/GreetingResource.java:10` — contoh `@RunOnVirtualThread`.
*   `docker-compose.yml:2-16` — Postgres 18 service.

> Update guideline ini jika ada keputusan baru (mis. tambah `pg_trgm`, ganti `hash`→`btree` untuk UUID). Jangan ubah DDL yang sudah migrate tanpa buat `V__` baru.
