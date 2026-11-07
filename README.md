# 📄 my-scan-doc-api

**my-scan-doc-api** est une API Spring Boot 4 / Java 21 qui permet de :
- 📤 **Uploader** des documents scannés (PDF ou images) vers un stockage **compatible S3** (Supabase Storage en production, MinIO en local).
- 📥 **Télécharger** des documents via leur identifiant unique.
- 💾 **Gérer les métadonnées** des documents dans PostgreSQL (Supabase en production).
- 🖼️ **Convertir les images en PDF** ; si ce n'est pas possible, les conserver telles quelles dans une archive **ZIP** (sans aucune perte).
- 🛡️ Valider le type du fichier par son contenu (taille ≤ 20 Mo) et dédupliquer par **SHA-256**.
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

Copie `.env.example` en `.env` puis remplace les valeurs. Variables :

| Variable | Rôle |
|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC PostgreSQL, par exemple `jdbc:postgresql://<host>:5432/postgres?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Utilisateur PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Mot de passe PostgreSQL |
| `APP_AUTH_USERNAME` | Nom du compte de service qui se connecte à l'API |
| `APP_AUTH_PASSWORD_HASH` | Hash **BCrypt** du mot de passe du compte de service (jamais le mot de passe en clair) |
| `APP_JWT_SECRET` | Secret de signature JWT, 256 bits minimum (32 caractères ou plus) |
| `APP_JWT_EXPIRATION_MINUTES` | Durée de validité d'un jeton (optionnel, `30` par défaut) |
| `APP_DDL_AUTO` | `update` (crée les tables) ou `validate` (production, une fois les tables créées). `update` par défaut |
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
APP_AUTH_USERNAME=<compte-de-service>
APP_AUTH_PASSWORD_HASH=<hash-bcrypt>
APP_JWT_SECRET=<secret-de-64-caractères-minimum>
APP_S3_ENDPOINT=https://<project-ref>.storage.supabase.co/storage/v1/s3
APP_S3_REGION=eu-west-2
APP_S3_BUCKET=scanned-docs
APP_S3_ACCESS_KEY=<access-key-id>
APP_S3_SECRET_KEY=<secret-access-key>
```

### Compte de service
L'API n'a qu'un compte, défini par `APP_AUTH_USERNAME` et `APP_AUTH_PASSWORD_HASH`. Pour générer le hash d'un mot de passe :

```
python -c "import bcrypt,getpass; print(bcrypt.hashpw(getpass.getpass().encode(), bcrypt.gensalt(12)).decode())"
```

(nécessite `pip install bcrypt`). Colle le résultat dans `.env`. L'application refuse de démarrer si le hash n'est pas au format BCrypt.

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
`POST /auth/login` avec un corps JSON `{"username": "...", "password": "..."}` (identifiants du compte de service). La réponse contient un champ `token` à passer dans l'en-tête `Authorization: Bearer <token>` pour les appels à `/api/**`.

### 1. Upload d'un document (PDF ou image)

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
  "key": "2025/08/14/uuid.pdf",
  "contentType": "application/pdf"
}
```

**Formats acceptés.** Le type est reconnu par le **contenu** du fichier ; le `Content-Type` et l'extension envoyés par le client sont ignorés.

| Fichier reçu | Ce qui est stocké | `contentType` renvoyé | Nom au téléchargement |
|---|---|---|---|
| PDF | le PDF tel quel | `application/pdf` | nom d'origine |
| Image JPEG, PNG, GIF, BMP, TIFF | un **PDF** (une page A4, image sans perte) | `application/pdf` | `nom.pdf` |
| Image impossible à convertir (WebP, HEIC/HEIF/AVIF, fichier corrompu, plus de 20 mégapixels) | une archive **ZIP** contenant l'image d'origine, octet pour octet | `application/zip` | `nom.webp.zip` |
| Tout autre format | refusé (`400`) | — | — |

La réponse indique dans `contentType` ce qui a réellement été stocké. La déduplication (SHA-256) porte sur le fichier **reçu**, pas sur le PDF produit : envoyer deux fois la même image renvoie le même document.

### 2. Télécharger un document

`GET /api/download/{id}`

```
curl -OJ -H "Authorization: Bearer <token>" \
     http://localhost:8080/api/download/6d9b2f41-5e8d-4b6b-91cc-89e3b5a68b83
```

(`-OJ` conserve le nom de fichier original)

---

### 3. Lister, archiver, restaurer, supprimer

Tous ces appels exigent le jeton JWT (`Authorization: Bearer …`).

| Appel | Rôle | Réponse |
|---|---|---|
| `GET /api/documents` | Documents actifs, du plus récent au plus ancien | `200` + liste |
| `GET /api/documents?archived=true` | Documents archivés | `200` + liste |
| `POST /api/documents/{id}/archive` | Archive un document (sans effet s'il l'est déjà) | `200` + document |
| `POST /api/documents/{id}/unarchive` | Restaure un document archivé | `200` + document |
| `DELETE /api/documents/{id}` | Supprime définitivement (base puis fichier) | `204` |

Chaque élément de la liste contient `id`, `name`, `size`, `contentType`, `createdAt` et `archivedAt` (`null` tant que le document n'est pas archivé). La clé de stockage et le SHA-256 ne sont jamais exposés. Un `id` inconnu renvoie `404`. Un document archivé reste téléchargeable.

Après une suppression, le même fichier peut être déposé de nouveau (la déduplication ne le reconnaît plus).

## 🔍 Fonctionnement

**Upload**
- Validation : taille ≤ 20 Mo, type reconnu par signature (PDF ou image).
- Image : conversion en PDF (PDFBox) ; en cas d'impossibilité, archive ZIP de l'image d'origine.
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

Les tests d'intégration utilisent **Testcontainers** et nécessitent **Docker**. Les tests définissent leurs propres identifiants et leur propre secret JWT : ils ne dépendent pas de `.env`.

Couverture :
- Upload OK / KO
- Download OK / 404
- Sécurité JWT (401 sans token ou avec un token invalide)
- Flux complet upload → stockage S3 + PostgreSQL → download

---

## 🔒 Sécurité

Le détail complet (mesures en place, gestion des secrets, checklist avant mise en ligne, risques résiduels) est dans **[SECURITY.md](SECURITY.md)**. En résumé :

- Authentification par JWT court (30 min), mot de passe stocké sous forme de hash BCrypt, compte unique défini dans `.env`.
- Accès fermé par défaut, HTTP Basic désactivé, erreurs sans détails internes, en-têtes de sécurité, bucket privé.
- Aucun secret dans le dépôt : `.env` est ignoré par Git et Docker, `.env.example` sert de modèle.
- **Avant la mise en ligne** : activer RLS sur la table `documents` dans Supabase et renouveler les secrets qui ont circulé hors de `.env` (checklist dans SECURITY.md).

## ⚠️ Points d'attention

- **Compte unique** : un seul compte de service existe. Pour gérer plusieurs utilisateurs, il faudra passer à une authentification en base.
- **Conversion des images** : l'image est placée sans perte sur une page A4 (portrait ou paysage). Les métadonnées d'orientation des photos (EXIF) ne sont pas traitées par l'application. Au-delà de 20 mégapixels, l'image n'est pas décodée (protection mémoire) : elle est archivée telle quelle dans un ZIP.
- **Schéma de base** : `APP_DDL_AUTO=update` crée et modifie les tables automatiquement, sans migration versionnée. Passer à `validate` en production une fois les tables créées.
- **Pas de limite d'essais sur `/auth/login`** : voir SECURITY.md, section « Limites connues ».

---

## 👨‍💻 Auteur

Mouhsin Ghoraf – Développeur Java / Angular
Contact : ghoraf.mouhsin@gmail.com
