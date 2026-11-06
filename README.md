# my-scan-doc-api

# 📄 my-scan-doc-api

**my-scan-doc-api** est une API Spring Boot 4 / Java 21 permettant de :
- 📤 **Uploader** des documents scannés (PDF) vers un stockage **S3-compatible** (MinIO en dev).
- 📥 **Télécharger** des documents via leur identifiant unique.
- 💾 **Gérer les métadonnées** des documents dans PostgreSQL.
- 🛡️ Valider le format PDF, dédupliquer par **SHA-256**.
- 🔐 Sécuriser avec JWT.

## 🚀 Stack technique

- **Backend** : Spring Boot 4.1.x (Java 21, Spring Framework 7, Hibernate 7, Jackson 3)
- **Stockage objet** : MinIO (compatible AWS S3) — image `cgr.dev/chainguard/minio` (les images `minio/*` ne sont plus publiées sur Docker Hub)
- **Messaging** : aucun pour l'instant
- **Base de données** : PostgreSQL 16
- **ORM** : Spring Data JPA (Hibernate)
- **Tests** : JUnit 5 + MockMvc + Testcontainers (Postgres, MinIO)
- **Build** : Maven 3.9+
- **Containerisation** : Docker & Docker Compose


---

## 🛠️ Installation & démarrage

### 1️⃣ Cloner le dépôt

git clone https://github.com/ton-user/my-scan-doc-api.git
cd my-scan-doc-api

2️⃣ Lancer l’infrastructure Docker (MinIO + PostgreSQL)

docker-compose up -d

📌 Par défaut :

MinIO API : http://localhost:9000

MinIO Console : http://localhost:9001 (user: minio / pass: minio12345)

PostgreSQL : localhost:5432 (db: myscandoc, user: scan, pass: scanpwd)

⚠️ Le bucket scanned-docs est créé automatiquement par minio-init.


Lancer l’API

./mvnw spring-boot:run


📌 Endpoints REST
1. Upload d’un document PDF

POST /api/upload
Content-Type: multipart/form-data

Exemple cURL : curl -F "file=@/chemin/vers/doc.pdf;type=application/pdf" \
http://localhost:8080/api/upload

Réponse :

{
"id": "6d9b2f41-5e8d-4b6b-91cc-89e3b5a68b83",
"name": "doc.pdf",
"size": 15423,
"key": "2025/08/14/uuid.pdf"
}

2. Télécharger un document

GET /api/download/{id}

Exemple cURL : curl -OJ http://localhost:8080/api/download/6d9b2f41-5e8d-4b6b-91cc-89e3b5a68b83

(-OJ conserve le nom de fichier original)

🔍 Fonctionnement

Upload :

Le fichier est validé (taille ≤ 20MB, signature %PDF-).

Calcul d’un SHA-256 pour déduplication.

Stockage dans S3/MinIO avec chemin YYYY/MM/DD/UUID.pdf.

Métadonnées persistées dans PostgreSQL.

Download :

Récupération des métadonnées en DB.

Lecture des octets depuis MinIO via l’API S3.

Retour HTTP avec Content-Disposition: attachment.

🧪 Tests

Lancer les tests (unitaires, MockMvc, et intégration Testcontainers — Docker requis) :
./mvnw test
📂 Couverture :

Upload OK / KO

Download OK / NotFound (404)

Sécurité JWT (401 sans token / token invalide)

Flux complet upload → MinIO + Postgres → download

🔒 Sécurité (optionnelle)

Ajouter Spring Security pour restreindre /api/** aux utilisateurs authentifiés.

Support JWT facilement intégrable.

👨‍💻 Auteur

Mouhsin – Développeur Java / Angular / DevOps

Contact : [ghoraf.mouhsin@gmail.com]
