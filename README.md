# 📄 my-scan-doc-api

**my-scan-doc-api** est une API Spring Boot 4 / Java 21 qui permet de :
- 📤 **Uploader** des documents scannés (PDF) vers un stockage **compatible S3** (Supabase Storage en production, MinIO en local).
- 📥 **Télécharger** des documents via leur identifiant unique.
- 💾 **Gérer les métadonnées** des documents dans PostgreSQL (Supabase en production).
- 🛡️ Valider le format PDF (taille ≤ 20 Mo, signature `%PDF-`) et dédupliquer par **SHA-256**.
- 🔐 Sécuriser les endpoints `/api/**` avec **JWT**.

## 🚀 Stack technique

- **Backend** : Spring Boot 4.1.x (Java 21, Spring Framework 7, Hibernate 7, Jackson 3)
- **Base de données** : PostgreSQL 16 (Supabase en production)
- **Stockage objet** : API S3 (Supabase Storage en production, MinIO en local)
- **ORM** : Spring Data JPA (Hibernate)
- **Tests** : JUnit 5, MockMvc, Testcontainers (PostgreSQL, MinIO)
- **Build** : Maven (wrapper `mvnw` fourni)
- **Conteneur** : Docker (image construite depuis le `Dockerfile`)

> Kafka et l'outbox ont été retirés du projet.

---

## ⚙️ Configuration

Toute la configuration est lue depuis un fichier **`.env`** à la racine du projet, ou depuis les variables d'environnement (qui sont prioritaires). Le fichier `.env` n'est **jamais versionné** (il est exclu par `.gitignore` et `.dockerignore`) car il contient des secrets.

Crée un fichier `.env` avec les variables suivantes :

| Variable | Rôle |
|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC PostgreSQL, par exemple `jdbc:postgresql://<host>:5432/postgres?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Utilisateur PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Mot de passe PostgreSQL |
| `APP_JWT_SECRET` | Secret de signature JWT, 256 bits minimum (32 caractères ou plus) |
| `APP_S3_ENDPOINT` | Endpoint S3 (voir ci-dessous) |
| `APP_S3_REGION` | Région S3 (optionnel, `eu-west-2` par défaut) |
| `APP_S3_BUCKET` | Nom du bucket, par exemple `scanned-docs` |
| `APP_S3_ACCESS_KEY` | Clé d'accès S3 |
| `APP_S3_SECRET_KEY` | Clé secrète S3 |
| `PORT` | Port HTTP (optionnel, `8080` par défaut) |

Exemple de structure (les valeurs sont à remplacer) :

```
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=<utilisateur>
SPRING_DATASOURCE_PASSWORD=<mot-de-passe>
APP_JWT_SECRET=<secret-de-64-caractères-minimum>
APP_S3_ENDPOINT=https://<project-ref>.storage.supabase.co/storage/v1/s3
APP_S3_REGION=eu-west-2
APP_S3_BUCKET=scanned-docs
APP_S3_ACCESS_KEY=<access-key-id>
APP_S3_SECRET_KEY=<secret-access-key>
```

### Supabase (production)
- **Base** : dans le tableau de bord, **Connect → Session pooler**. Utilise le host du pooler et le nom d'utilisateur `postgres.<project-ref>`. La connexion directe ne fonctionne pas depuis Render.
- **Stockage** : crée le bucket `scanned-docs` (privé), puis une clé d'accès dans **Storage → S3 Configuration**. L'endpoint est `https://<project-ref>.storage.supabase.co/storage/v1/s3`.

### Local
Pour utiliser PostgreSQL et MinIO en local, il faut les lancer séparément (installation native ou conteneurs), puis renseigner `.env` avec leurs adresses. Par défaut, MinIO écoute sur `http://localhost:9000`.

---

## 🛠️ Lancer l'application

### En local (Maven)

Depuis la racine du projet, avec `.env` présent :

```
.\mvnw.cmd spring-boot:run
```

Sous IntelliJ, le répertoire de travail doit être la racine du projet pour que `.env` soit trouvé.

### Avec Docker

```
docker build -t my-scan-doc-api .
docker run --rm --env-file .env -p 8080:8080 my-scan-doc-api
```

Sur Docker Desktop (Windows), les variables pointant vers `localhost` doivent être remplacées par `host.docker.internal` si les services tournent sur la machine hôte.

### Vérifier le démarrage

```
curl http://localhost:8080/actuator/health
```

Doit répondre `{"status":"UP"}`.

---

## ☁️ Déploiement sur Render

Le fichier `render.yaml` décrit un service Docker sur le plan gratuit, avec un health check sur `/actuator/health`.

1. Crée un **Blueprint** sur Render à partir du dépôt GitHub.
2. Renseigne dans le tableau de bord les variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_S3_ENDPOINT`, `APP_S3_ACCESS_KEY` et `APP_S3_SECRET_KEY`. `APP_JWT_SECRET` est généré automatiquement.
3. Le profil `prod` est activé via `SPRING_PROFILES_ACTIVE=prod`.

Sur le plan gratuit, le service s'endort après 15 minutes sans requête. Le premier appel après une pause prend environ une minute.

---

## 📌 Endpoints REST

### Authentification
`POST /auth/login` avec un corps JSON `{"username": "...", "password": "..."}`. La réponse contient un champ `token` à passer dans l'en-tête `Authorization: Bearer <token>` pour les appels à `/api/**`.

### 1. Upload d'un document PDF

`POST /api/upload` (multipart/form-data, champ `file`)

```
curl -H "Authorization: Bearer <token>" \
     -F "file=@/chemin/vers/doc.pdf;type=application/pdf" \
     http://localhost:8080/api/upload
```

Réponse :

```json
{
  "id": "6d9b2f41-5e8d-4b6b-91cc-89e3b5a68b83",
  "name": "doc.pdf",
  "size": 15423,
  "key": "2025/08/14/uuid.pdf"
}
```

### 2. Télécharger un document

`GET /api/download/{id}`

```
curl -OJ -H "Authorization: Bearer <token>" \
     http://localhost:8080/api/download/6d9b2f41-5e8d-4b6b-91cc-89e3b5a68b83
```

(`-OJ` conserve le nom de fichier original)

---

## 🔍 Fonctionnement

**Upload**
- Validation : taille ≤ 20 Mo, signature `%PDF-`.
- Calcul du SHA-256 ; si le même contenu existe déjà, le document existant est renvoyé.
- Stockage dans le bucket S3 sous le chemin `YYYY/MM/DD/UUID.pdf`.
- Métadonnées enregistrées dans PostgreSQL.

**Download**
- Récupération des métadonnées en base.
- Lecture des octets depuis le stockage S3.
- Réponse HTTP avec `Content-Disposition: attachment`.

---

## 🧪 Tests

```
.\mvnw.cmd test
```

Les tests d'intégration utilisent **Testcontainers** et nécessitent **Docker**. Les tests lisent aussi la configuration de `.env` : sans ce fichier, `APP_JWT_SECRET` doit être fourni par une variable d'environnement.

Couverture :
- Upload OK / KO
- Download OK / 404
- Sécurité JWT (401 sans token ou avec un token invalide)
- Flux complet upload → stockage S3 + PostgreSQL → download

---

## ⚠️ Points d'attention

- **Compte administrateur** : l'utilisateur `admin` est défini en dur dans `SecurityConfig`. À remplacer par une authentification en base ou par des variables d'environnement avant toute mise en production publique.
- **Migrations** : `spring.jpa.hibernate.ddl-auto` est à `update`. Les tables sont créées et modifiées automatiquement, sans migration versionnée.

---

## 👨‍💻 Auteur

Mouhsin Ghoraf – Développeur Java / Angular
Contact : ghoraf.mouhsin@gmail.com
