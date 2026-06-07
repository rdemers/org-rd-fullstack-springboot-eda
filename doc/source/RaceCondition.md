Bonne observation — explorons le code de traitement pour te donner une explication fondée plutôt que spéculative. Lisons `PipelineProcessorSrv` en entier et la définition du schéma `inventory`.

Le code de traitement confirme la présence d'une vraie condition de course. Regardons aussi le repository (verrou ?) et les données de départ (DatabaseConfig) pour expliquer pourquoi ça ne s'est pas manifesté.

Excellente intuition — et la réponse est très instructive : **la condition de course existe bel et bien dans ton code. Si elle ne se manifeste pas, c'est ta base de données qui te « sauve », pas ton code.**

## La course est réelle dans `process()`

```java
// 1) Lecture NON verrouillée (simple SELECT)
Optional<Inventory> inv = inventoryRepository.findByProductId(request.getProductId());
...
// 2) Vérification applicative sur cette lecture
if (inventory.getQty() < request.getQty()) { BACK_ORDER; return; }
// 3) Décrément RELATIF
inventoryRepository.creditQTY(request.getQty(), inventory.getInventoryId());  // qty = qty - :qty
```

C'est un **check-then-act** classique (TOCTOU). En théorie, avec deux threads pour le même produit, stock=10, deux CREDIT(10) :
- T1 lit 10 → check OK → 0
- T2 lit 10 (avant le commit de T1) → check OK → puis `qty = qty - 10` appliqué sur la valeur courante → **−10**

`findByProductId` ne pose **aucun verrou**, `Inventory` n'a **pas de `@Version`**, et l'isolation est `READ_COMMITTED`. Rien dans le code n'empêche le survente.

## Pourquoi ça n'arrive pas chez toi : HSQLDB

Ton datasource :
```
url: jdbc:hsqldb:mem:stagingdb
```

HSQLDB démarre par défaut en mode **`TRANSACTION CONTROL LOCKS`** = verrouillage à deux phases (2PL), **au niveau table** pour les écritures. Conséquence :

- Dès que T1 exécute le `UPDATE` sur `inventory`, il pose un **write-lock sur toute la table** jusqu'au commit.
- Le `findByProductId` de T2 (read-lock) **bloque** jusqu'à ce que T1 commit, puis lit la valeur **fraîche** (post-décrément).
- Donc le check-then-act devient **effectivement atomique** : T2 voit 0, son check `0 < 10` → `BACK_ORDER`. Jamais négatif.

Autrement dit, HSQLDB **sérialise** toutes les transactions qui écrivent dans `inventory`. Tes 10 threads consommateurs sont, de fait, mis en file d'attente au niveau DB pour ce table-là. Ton code est « accidentellement correct ».

## Ça casserait sur une vraie base MVCC

Sur **PostgreSQL / MySQL (InnoDB)** en `READ_COMMITTED`, les lecteurs ne bloquent pas les écrivains (MVCC). Les deux `findByProductId` liraient 10, passeraient le check, et les deux `UPDATE` relatifs donneraient **−10**. Le bug apparaîtrait. Tu peux d'ailleurs le reproduire sur HSQLDB en forçant `SET DATABASE TRANSACTION CONTROL MVCC`.

## Le lien avec ta question précédente sur les clés 🔑

C'est exactement l'intérêt de publier **avec clé** : avec `key = productId`, tous les messages d'un même produit vont sur la **même partition** → traités **séquentiellement par un seul thread** → aucune course, **quelle que soit la base**. Sans clé, tu dépends de la chance (ou ici, du verrouillage HSQLDB).

## Pour rendre le code robuste *indépendamment* de la base

Par ordre de préférence :

1. **Update conditionnel atomique** (le plus propre, sans verrou) :
   ```java
   @Modifying
   @Query("UPDATE Inventory i SET i.qty = i.qty - :qty WHERE i.inventoryId = :id AND i.qty >= :qty")
   int tryCredit(@Param("qty") Long qty, @Param("id") Long id);
   ```
   Si `tryCredit(...) == 0` → pas assez de stock → `BACK_ORDER`. Le `AND i.qty >= :qty` élimine le TOCTOU dans une seule instruction atomique.

2. **Verrou pessimiste** sur la lecture : `@Lock(LockModeType.PESSIMISTIC_WRITE)` sur `findByProductId` (= `SELECT ... FOR UPDATE`).

3. **Verrou optimiste** : `@Version` sur `Inventory` + retry sur `OptimisticLockException`.

4. **Filet de sécurité DB** : contrainte `CHECK (qty >= 0)` → l'update fautif échoue et part au DLT.

Veux-tu que j'implémente l'option 1 (update conditionnel) ? C'est le correctif le plus net et il garde ta logique `BACK_ORDER` intacte tout en supprimant la course pour de bon.

Oui, c'est activable — mais je veux être honnête avec toi sur **ce que tu vas réellement observer**, parce que HSQLDB en MVCC ne se comporte pas *exactement* comme PostgreSQL en `READ_COMMITTED`.

## Comment l'activer

Le plus simple : une propriété dans l'URL JDBC ([application.yml](src/main/resources/application.yml)).

```yaml
url: jdbc:hsqldb:mem:stagingdb;DB_CLOSE_DELAY=-1;hsqldb.tx=mvcc
```

(`hsqldb.tx` accepte `locks` [défaut], `mvlocks`, `mvcc`.) Équivalent SQL si tu préfères l'exécuter au démarrage : `SET DATABASE TRANSACTION CONTROL MVCC`.

Ça supprime le verrouillage table-level qui « sérialisait » tout et masquait la course. **Donc oui, le hasard de concurrence devient observable sans clé.**

## ⚠️ Mais le symptôme ne sera pas le même que PostgreSQL

| | PostgreSQL `READ COMMITTED` | HSQLDB `MVCC` |
|---|---|---|
| 2 CREDIT concurrents, même produit | Le 2ᵉ `UPDATE` **bloque**, puis ré-applique le décrément relatif sur la valeur fraîche → **solde négatif silencieux** | Le 2ᵉ écrivain détecte un conflit d'écriture et **lève une exception de sérialisation** (`SQLState 40001`), il n'y a pas de négatif silencieux |

Concrètement, en HSQLDB MVCC, quand deux threads touchent la **même** ligne d'inventaire :
- la transaction perdante **rollback** avec une *serialization failure*,
- ton `DefaultErrorHandler` Kafka **rejoue** le message,
- au replay, l'autre transaction a committé → relecture de la valeur à jour → le check `BACK_ORDER` fonctionne.

Donc tu **ne verras pas** l'inventaire « tomber sous zéro ». Tu verras plutôt : **des exceptions de conflit / des retries** sans clé, et **rien** avec clé. C'est une démonstration tout aussi valable de la pertinence de la clé (sans clé = anomalies de concurrence ; avec clé = traitement sérialisé par produit, aucun conflit), mais le symptôme visible diffère.

À noter : comme les conflits sont **transitoires**, les retries finissent souvent par réussir → tu risques de ne voir que des *warnings* de retry, sans forcément de DLT ni d'incrément du compteur d'erreurs. Pour rendre la démo plus spectaculaire, on peut baisser le nombre de retries pour pousser les conflits vers le DLT (et faire grimper `nbrProcessedWithError`).

## Si tu veux LE négatif « à la PostgreSQL »

Le solde réellement négatif et silencieux est un comportement spécifique de PostgreSQL/MySQL en `READ COMMITTED` (ré-évaluation du `UPDATE` relatif sur la dernière version committée, sans exception). HSQLDB, même en MVCC, ne le reproduit pas — il préfère lever 40001. Pour ce symptôme précis, il faudrait un vrai PostgreSQL.

## En résumé

- **« Est-ce possible ? »** → Oui, `;hsqldb.tx=mvcc` dans l'URL.
- **« Est-ce que ça fait le travail ? »** → Oui pour **démontrer que l'absence de clé provoque des anomalies de concurrence** (conflits/retries) qui **disparaissent avec la clé**. Non si tu tiens absolument au symptôme « solde négatif » — ça, c'est PostgreSQL.

Veux-tu que :
1. j'applique `;hsqldb.tx=mvcc` dans `application.yml` (+ éventuellement réduire les retries Kafka pour que les conflits soient bien visibles), ou
2. on regarde plutôt comment brancher un PostgreSQL pour obtenir le négatif silencieux ?


Bonne question. Le point clé : ton symptôme recherché (survente **silencieuse** → solde négatif) dépend de la *famille* de moteur de concurrence, pas du fait qu'il soit embarqué.

Laisse-moi d'abord vérifier comment ton schéma est créé (ça conditionne l'effort de migration).

Ton schéma est créé par `schema.sql` (init SQL Spring, `ddl-auto: none`), et seul HSQLDB est en dépendance. Voici l'analyse par **famille de moteur**, parce que c'est ça qui décide du symptôme :

## Pourquoi H2 ne réglera pas le problème non plus

| Famille | Exemples embarqués | Conflit d'écriture concurrente (même ligne) | Symptôme |
|---|---|---|---|
| **MVCC / snapshot** | **H2** (MVStore, défaut), HSQLDB‑mvcc | détecte le conflit → **lève une exception** (`90131 concurrent update` / `40001`) | erreurs/retries, **pas** de négatif silencieux |
| **Verrouillage 2PL** *avec ré‑évaluation* du `UPDATE` relatif en READ_COMMITTED | **Apache Derby**, PostgreSQL, MySQL/InnoDB | la 2ᵉ écriture **attend** le verrou, puis ré‑applique `qty = qty − :q` sur la valeur fraîche | **survente silencieuse → solde négatif** ✓ |

H2 (le « embarqué » par défaut de Spring Boot) est MVCC → même comportement que HSQLDB‑mvcc : il **lèvera une exception**, pas de négatif. Donc H2 ne t'aide pas.

## La réponse : **Apache Derby** (embarqué, pur Java)

Derby est un moteur **2PL pur, sans détection optimiste de conflit**. En `READ_COMMITTED` (son défaut), la 2ᵉ transaction attend le verrou exclusif, puis exécute simplement le décrément relatif sur la valeur courante → **solde négatif silencieux**, exactement comme PostgreSQL/MySQL.

- ✅ Embarqué, in‑memory (`jdbc:derby:memory:stagingdb;create=true`), géré par Spring Boot, **sans Docker**.
- ⚠️ C'est une **vraie** course → **intermittent** (parfois négatif, parfois `BACK_ORDER` selon le timing). C'est en fait pédagogiquement honnête : « sans clé, c'est non‑déterministe et dangereux ; avec clé, c'est sérialisé par produit et toujours correct ».

**Effort de migration** (modéré) : ajouter la dépendance Derby, changer `url` / `driver-class-name` / dialecte Hibernate, et **porter `schema.sql` en syntaxe Derby** (les deux supportent `GENERATED ... AS IDENTITY`, donc le port est généralement léger).

## L'alternative la plus fidèle : PostgreSQL via Spring Boot

Si Docker est disponible dans ton environnement de cours, le plus fidèle (et le plus « vrai monde ») est un **vrai PostgreSQL** géré automatiquement par Spring Boot :
- **`spring-boot-docker-compose`** (+ un `compose.yaml`) → Spring démarre/arrête le conteneur tout seul, ou
- **Testcontainers**.

Tu obtiens le comportement Postgres `READ_COMMITTED` garanti, sans deviner de dialecte. Ce n'est pas « embarqué » au sens pur Java, mais le cycle de vie est automatique.

## Ma recommandation

- **Pas de Docker / 100 % embarqué** → **Derby** in‑memory.
- **Docker dispo** → **PostgreSQL** via `spring-boot-docker-compose` (idéal pour un cours : tu démontres sur la vraie cible).

Les deux demandent à peu près le même travail (dépendance + datasource + port de `schema.sql`).

Quelle voie veux‑tu que je mette en place ?

Ta prudence est justifiée — disons que « orphelin » est un peu fort, mais « en sommeil / faible activité » est juste.

## L'état réel de Derby

- **Pas officiellement abandonné** : Derby est toujours un projet Apache actif au sens formel (il n'est **pas** passé à l'Apache Attic). La branche récente (10.16 / 10.17) supporte Java 17+.
- **Mais activité très faible** : releases rares et espacées, communauté réduite, peu de commits. 
- **« JavaDB » a été retiré du JDK** il y a longtemps (≈ Java 9), donc il n'est plus livré avec Java — ce qui renforce sa perception « legacy ».

Donc ton instinct est bon : pour un cours, enseigner avec un moteur perçu comme moribond, ce n'est pas idéal côté optique.

## Le point gênant

Parmi les BD **embarquées pur Java**, le classement par concurrence est sans appel :

| BD embarquée | Maintenue activement ? | Reproduit la survente silencieuse ? |
|---|---|---|
| H2 | ✅ oui (très active) | ❌ non (MVCC → exception) |
| HSQLDB | ✅ oui | ❌ non (LOCKS sérialise, ou MVCC → exception) |
| **Derby** | ⚠️ faible activité | ✅ **oui** (2PL, négatif silencieux) |
| SQLite (xerial) | ✅ oui | ❌ non (verrou global → sérialise tout) |

Autrement dit : **les seules BD embarquées bien maintenues (H2, HSQLDB) sont précisément celles qui *ne* reproduisent *pas* ton symptôme.** Derby est le seul embarqué dans la bonne famille « 2PL » — et c'est justement celui dont la santé t'inquiète. Il n'y a pas d'embarqué « actif **et** verrouillage 2PL » sous la main.

## Conclusion pragmatique

Tu as deux choix cohérents :

1. **Accepter Derby quand même** — pour un usage **dev/test uniquement**, qui sert à *démontrer un comportement* en cours, la cadence de maintenance n'a aucune importance réelle (pas de prod, pas d'exposition sécurité, ça tourne en mémoire 5 minutes). Le « legacy » est purement cosmétique ici.

2. **Passer à PostgreSQL via `spring-boot-docker-compose`** — activement maintenu, c'est la **vraie cible**, comportement `READ_COMMITTED` garanti, cycle de vie géré par Spring Boot. Le prix : il faut Docker dans l'environnement du cours.

Vu ta réticence envers Derby, **PostgreSQL via Docker Compose** est probablement le meilleur choix pédagogique : tu démontres sur le moteur réel, sans dépendre d'un projet en sommeil. Le seul prérequis est Docker.

👉 Docker est-il disponible dans l'environnement où tu donneras la formation ? Si oui, je te branche PostgreSQL via `spring-boot-docker-compose` (dépendance + `compose.yaml` + datasource + port de `schema.sql`). Sinon, on assume Derby.

On applique. D'abord l'URL HSQLDB en mode MVCC :

Maintenant, la configuration des retries / `DefaultErrorHandler` dans KafkaConfig :

Le `DefaultErrorHandler` (et donc le nombre de retries / le `BackOff`) est construit dans `KafkaSandbox.getDefaultErrorHandler()`. Regardons-le :

Voyons `getDefaultErrorHandler()` (ligne 647) pour savoir quelle valeur de retries est réellement utilisée, et la source de `retryAttempts` :

C'est `buildErrorHandler(template)` (sans args) qui est utilisé → il s'appuie sur `KafkaConstants.CST_RETRY_ATTEMPTS` / `CST_RETRY_INTERVAL`. Regardons leurs valeurs actuelles :

`getDefaultErrorHandler()` utilise `KafkaConstants.CST_RETRY_ATTEMPTS = 3` (donc 1 essai + 3 retries = 4 tentatives) avec un intervalle de 1000 ms. Je réduis les retries pour que les conflits aient moins de chances de « s'auto-guérir » et atterrissent au DLT (→ visibles via `nbrProcessedWithError` / état EXCEPTION).

Compile (exit 0). C'est appliqué.

## Modifications

**1. HSQLDB en mode MVCC** — [application.yml](src/main/resources/application.yml)
```yaml
url: jdbc:hsqldb:mem:stagingdb;DB_CLOSE_DELAY=-1;hsqldb.tx=mvcc
```
Plus de sérialisation table-level → les transactions concurrentes sur le même produit entrent désormais en **conflit** au lieu d'être mises en file.

**2. Retries réduits** — [KafkaConstants.java](src/main/java/org/rd/fullstack/springbooteda/util/kafka/KafkaConstants.java)
- `CST_RETRY_ATTEMPTS` : 3 → **1** (1 essai + 1 retry = 2 tentatives au lieu de 4)
- `CST_RETRY_INTERVAL` : 1000 → **250 ms** (le retry survient plus tôt, pendant que la contention est encore active)

→ Un message en conflit a moins de chances de « s'auto-guérir » et part plus vite au **DLT**, ce qui incrémente `nbrProcessedWithError` (visible dans l'onglet Résultats / état `EXCEPTION`).

## ⚠️ Honnêteté sur le résultat attendu

- Sans clé : tu devrais voir des **exceptions de conflit (`40001`)** → retries → certains messages au **DLT**. Avec clé : 0 conflit.
- **Mais c'est probabiliste.** Les transactions DB sont très rapides ; même avec 1 retry, beaucoup de conflits se résolvent au 2ᵉ essai. La visibilité dépend du volume de requêtes **pour un même produit** traitées simultanément (10 threads × 10 produits × stock 100, CREDIT de 10 → il faut de la contention soutenue).
- Rappel : ce ne sera **pas** un solde négatif silencieux (ça, c'est PostgreSQL) — c'est des **conflits/erreurs** qui disparaissent avec la clé. La démonstration de la pertinence de la clé tient quand même : *sans clé = anomalies ; avec clé = traitement sérialisé par produit, zéro conflit.*

## Pour une démo *déterministe* (option)

Si tu veux qu'**un seul** conflit suffise à envoyer le message au DLT (au lieu de compter sur l'épuisement des retries), on peut marquer l'exception de conflit comme **non-retryable** dans le `DefaultErrorHandler` (`addNotRetryableExceptions(...)`). Premier conflit → DLT immédiat → compteur d'erreurs garanti. Il faudra juste confirmer la classe exacte d'exception (visible dans les en-têtes du DLT / les logs lors de ta répétition). Tu veux que je l'ajoute ?

Pense à **redémarrer proprement** (pas un simple hot-reload) pour que la nouvelle URL HSQLDB prenne effet, puis lance le pipeline sans clé.