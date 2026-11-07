# 🔒 Sécurité — my-scan-doc-api

Ce document décrit **ce qui est protégé, comment, et ce qui reste à faire**. Il est à jour avec le code : toute modification de la sécurité doit être reportée ici.

## 1. Périmètre

L'API reçoit des **PDF scannés potentiellement sensibles** (documents personnels, administratifs). Elle est appelée par des **services** (pas par des navigateurs) avec un compte unique.

| Élément | Hébergement |
|---|---|
| API Spring Boot | Render (conteneur Docker) |
| Base PostgreSQL (métadonnées) | Supabase |
| Fichiers PDF | Supabase Storage (API S3, bucket **privé**) |

**Menaces prises en compte** : accès non authentifié, fuite de secrets, vol ou rejeu de jeton, fichier malveillant envoyé à l'upload, fuite d'informations par les messages d'erreur, exposition involontaire de la base via l'API publique de Supabase.

## 2. Mesures en place

| # | Mesure | Où | Pourquoi |
|---|---|---|---|
| 1 | **Authentification par JWT uniquement** (signature HS256, expiration 30 min par défaut) | `JwtService`, `JwtAuthFilter` | Pas de session ni de cookie à voler |
| 2 | **Mot de passe stocké sous forme de hash BCrypt**, jamais en clair | `.env` → `APP_AUTH_PASSWORD_HASH`, `SecurityConfig` | Une fuite de `.env` ne révèle pas le mot de passe. L'application refuse de démarrer si ce n'est pas un hash BCrypt |
| 3 | **Compte unique défini hors du code** | `APP_AUTH_USERNAME`, `APP_AUTH_PASSWORD_HASH` | Plus aucun identifiant dans le dépôt |
| 4 | **Accès fermé par défaut** : seules `/auth/login`, `/actuator/health` et `/error` sont publiques, `/api/**` exige un jeton, tout le reste est refusé | `SecurityConfig` | Une route ajoutée par erreur n'est pas exposée |
| 5 | **HTTP Basic et formulaire de connexion désactivés** | `SecurityConfig` | Évite un second chemin d'authentification sans limite d'essais |
| 6 | **Réponses cohérentes** : non authentifié → 401, interdit → 403, message d'échec identique pour utilisateur inconnu et mauvais mot de passe | `SecurityConfig`, `RestExceptionHandler` | Pas d'énumération de comptes |
| 7 | **CSRF désactivé volontairement** | `SecurityConfig` | API sans session ni cookie : il n'y a rien à détourner |
| 8 | **En-têtes de sécurité** : `Content-Security-Policy: default-src 'none'`, `Referrer-Policy: no-referrer`, plus les défauts de Spring Security (`X-Content-Type-Options: nosniff`, `X-Frame-Options`, `Cache-Control: no-store`) | `SecurityConfig` | L'API ne sert aucune page : rien ne doit être interprété par un navigateur |
| 9 | **HSTS** pris en compte derrière le proxy de Render | `server.forward-headers-strategy: framework` | Spring sait que la requête d'origine est en HTTPS |
| 10 | **Erreurs sans détails internes** : pas de message, de stacktrace ni d'exception dans les réponses ; le détail va dans les logs | `server.error.*`, `RestExceptionHandler` | Pas de fuite d'informations techniques |
| 11 | **Actuator limité à `/actuator/health`**, sans détails | `management.*` | Pas d'exposition de métriques ou de configuration |
| 12 | **Validation des uploads** : taille ≤ 20 Mo, type reconnu par **signature binaire** (PDF ou image JPEG, PNG, GIF, BMP, TIFF, WebP, HEIC) — le `Content-Type` envoyé par le client est ignoré —, images limitées à **20 mégapixels** (vérifié avant tout décodage), déduplication SHA-256 du fichier reçu | `DocumentService`, `FileSignature`, `PdfBoxImageToPdfConverter`, `spring.servlet.multipart` | Rejette les fichiers d'un autre type et les images « bombes » qui saturent la mémoire (elles sont archivées en ZIP sans être décodées) |
| 13 | **Le nom de fichier envoyé par le client n'est jamais utilisé comme chemin** : la clé de stockage est un UUID généré côté serveur (`AAAA/MM/JJ/<uuid>.pdf`) | `S3StorageAdapter` | Pas de traversée de répertoire. Le nom d'origine est seulement renvoyé dans `Content-Disposition`, encodé selon la RFC 5987. Dans une archive ZIP, le nom d'entrée est assaini (sans dossier, sans `..`, caractères limités à `A-Za-z0-9._ -`) pour éviter une attaque « zip slip » à l'extraction |
| 14 | **Bucket privé** : aucun fichier n'est accessible sans passer par l'API | Supabase Storage | Pas d'URL publique |
| 15 | **Secrets hors du dépôt** : `.env` est exclu de Git (`.gitignore`) et de l'image Docker (`.dockerignore`) | `.gitignore`, `.dockerignore` | Aucun secret versionné ni embarqué dans l'image |
| 16 | **Conteneur non-root**, image JRE seule (pas de Maven ni de sources dans l'image finale) | `Dockerfile` | Réduit l'impact d'une compromission |
| 17 | **Secret JWT ≥ 256 bits imposé** : l'application ne démarre pas avec un secret trop court | `JwtService` | Empêche un secret faible |
| 18 | **Profil `prod`** : pas de SQL dans les logs, journaux de sécurité limités aux avertissements, arrêt propre, pool de connexions réduit | `application-prod.yml` | Évite les fuites de données dans les logs et les coupures en plein upload |
| 19 | **Mises à jour des dépendances suivies** | `.github/dependabot.yml` | Les failles connues (Maven, image Docker) sont signalées chaque semaine par une pull request |
| 20 | **Suppression ordonnée** : la ligne en base est supprimée avant le fichier du stockage ; un échec du stockage est journalisé et ne casse pas la suppression | `DocumentService.delete` | Un fichier orphelin est inoffensif, une ligne qui pointe vers un fichier absent ne l'est pas. Les réponses de liste n'exposent ni clé de stockage ni SHA-256 |

## 3. Gestion des secrets

Tous les secrets vivent dans `.env` (local) ou dans les variables d'environnement de Render (production). Le modèle sans valeurs est `.env.example` (versionné).

| Variable | Sensibilité | Comment la changer |
|---|---|---|
| `APP_JWT_SECRET` | Critique : permet de forger des jetons | Générer un nouveau secret : `python -c "import secrets; print(secrets.token_urlsafe(48))"`. **Tous les jetons existants deviennent invalides** |
| `APP_AUTH_PASSWORD_HASH` | Élevée | Générer un nouveau hash (commande dans le README) et redéployer |
| `SPRING_DATASOURCE_PASSWORD` | Critique : accès complet aux métadonnées | Supabase → *Project Settings → Database → Reset database password*, puis mettre à jour l'environnement |
| `APP_S3_ACCESS_KEY` / `APP_S3_SECRET_KEY` | Critique : **accès à tous les buckets du projet** | Supabase → *Storage → S3 Configuration* : supprimer la clé, en créer une nouvelle |

**Règles**
- Toute valeur affichée ou collée dans un chat, un ticket, un log ou une capture d'écran est **considérée comme compromise** : la renouveler.
- Ne jamais committer `.env`. Vérification : `git check-ignore -v .env` doit afficher la règle `.gitignore`.
- Sur Render, les valeurs sensibles se saisissent dans le tableau de bord (variables « sync: false » de `render.yaml`), jamais dans le dépôt.

**Historique Git** : d'anciennes versions de `application.yml` contiennent des identifiants de **développement local** (base `scan`, MinIO `minio`) et un secret JWT d'exemple. Ils ne servent pas en production et ne doivent **jamais** être réutilisés. Si le dépôt est public ou le devient, considérer ces valeurs comme connues.

## 4. Checklist avant la mise en ligne

À cocher une fois, puis à revérifier après chaque changement d'hébergement.

- [ ] **Supabase — activer la sécurité au niveau des lignes (RLS) sur la table `documents`.** Les tables du schéma `public` sont exposées par défaut par l'API REST de Supabase : sans RLS, quiconque possède la clé publique du projet peut les lire. Après le premier démarrage (la table est créée par l'application), exécuter dans l'éditeur SQL :
  ```sql
  ALTER TABLE public.documents ENABLE ROW LEVEL SECURITY;
  ```
  Sans politique, l'accès par l'API REST est refusé, alors que l'application (rôle `postgres`, qui contourne RLS) continue de fonctionner. Contrôler ensuite dans *Advisors → Security Advisor* qu'aucune alerte ne reste.
- [ ] **Passer `APP_DDL_AUTO=validate`** après la création des tables : l'application ne modifie plus jamais le schéma.
- [ ] **Renouveler les secrets** qui ont circulé hors de `.env` (clés S3, mot de passe de base, mot de passe du compte de service).
- [ ] **Bucket `scanned-docs` en privé** (désactiver « Public bucket »).
- [ ] **Mot de passe du compte de service fort** (≥ 20 caractères aléatoires), stocké dans un gestionnaire de mots de passe.
- [ ] **Supprimer le fichier `.admin-password.txt`** s'il existe encore.
- [ ] **URL de base avec `sslmode=require`** (déjà le cas dans le modèle).

**Renforcements possibles ensuite** : créer un rôle PostgreSQL dédié avec les seuls droits `SELECT, INSERT, UPDATE, DELETE` sur `documents` (utilisateur `scan_api.<project-ref>` côté pooler) plutôt que `postgres` ; utiliser `sslmode=verify-full` avec le certificat de Supabase.

## 5. Limites connues et risques résiduels

| Risque | Niveau | Détail | Atténuation |
|---|---|---|---|
| **Pas de limite d'essais sur `/auth/login`** | Moyen | Un attaquant peut tester des mots de passe sans blocage | Mot de passe long et aléatoire ; BCrypt (coût 12) ralentit chaque essai ; ajouter une limitation de débit en amont (Cloudflare, ou en code) si l'URL est publique |
| **Pas de révocation de jeton** | Faible | Un jeton volé reste valable jusqu'à son expiration | Durée courte (30 min) ; changer `APP_JWT_SECRET` invalide tout |
| **Compte unique** | Faible | Pas de rôles ni de traçabilité par utilisateur | Acceptable pour un compte de service ; prévoir une authentification en base si les usages se multiplient |
| **Contenu des fichiers non analysé** | Moyen | Seule la signature est vérifiée : un PDF ou une image piégé est accepté. Les images sont décodées côté serveur par PDFBox / ImageIO (surface d'attaque à garder à jour via Dependabot) | Les fichiers ne sont jamais interprétés ni servis à un navigateur (`nosniff`, `attachment`) ; ajouter un antivirus (ClamAV) si les fichiers viennent de tiers |
| **Clé S3 à portée de tout le projet** | Moyen | Elle donne accès à tous les buckets Supabase du projet | La garder uniquement dans l'environnement ; utiliser un projet Supabase dédié |
| **Plan gratuit** | Faible | Pas de sauvegarde garantie, mise en pause du projet Supabase après 7 jours d'inactivité | Exporter régulièrement les données si elles sont importantes |
| **Pas d'audit des accès** | Faible | Aucune trace de qui télécharge quoi | Les logs de Render conservent les requêtes ; ajouter un journal d'audit si nécessaire |
| **JWT sans `iss` / `aud`** | Faible | Le jeton n'est valable que pour cette API de toute façon (secret propre) | Ne jamais réutiliser `APP_JWT_SECRET` ailleurs |
| **`forward-headers-strategy: framework`** | Faible | Fait confiance aux en-têtes `X-Forwarded-*` | Ne pas exposer l'application directement à Internet sans proxy ; sur Render, le proxy les écrase |

## 6. Vérifications utiles

```
# .env bien ignoré par Git
git check-ignore -v .env

# Aucun secret versionné (doit ne rien afficher)
git grep -nE "(secret|password)\s*[:=]\s*[A-Za-z0-9+/]{16,}" -- . ":!SECURITY.md" ":!README.md" ":!src/test"

# Santé de l'API
curl https://<ton-service>.onrender.com/actuator/health

# Une route protégée sans jeton doit répondre 401
curl -i https://<ton-service>.onrender.com/api/download/00000000-0000-0000-0000-000000000000
```

## 7. Signaler une faille

Contact : ghoraf.mouhsin@gmail.com. Merci de ne pas ouvrir de ticket public pour une vulnérabilité.
