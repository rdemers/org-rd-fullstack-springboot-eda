# Patrons de conception Inbox et Outbox

> Partie du **Guide Kafka Engineering** de `org-rd-fullstack-springboot-eda`. Voir le [LISEZ_MOI du projet](./LISEZ_MOI.md).

**Portée :** Présentation et exemples d'implémentation des patrons de conception associés aux systèmes distribués, EDA, Apache Kafka et bases de données transactionnelles. Les exemples SQL ciblent principalement PostgreSQL et sont à adapter au SGBD retenu.

## Table des matières

1. [Contexte et problématique](#1-contexte-et-problématique)
2. [Principes et garanties](#2-principes-et-garanties)
3. [Transactional Outbox](#3-transactional-outbox)
4. [Transactional Inbox](#4-transactional-inbox)
5. [Combiner Inbox et Outbox](#5-combiner-inbox-et-outbox)
6. [Références](#6-références)

---

## 1. Contexte et problématique

Dans une architecture orientée événements (EDA), un service doit souvent **modifier une base de données** et **émettre un événement**. Un autre service doit ensuite **consommer cet événement** et **modifier sa propre base de données**. Ces opérations traversent des frontières transactionnelles différentes.

### 1.1 Le problème du double écrit (dual write)

Exemple : une commande est enregistrée dans PostgreSQL, puis `OrderCreated` est publié dans Kafka.

```text
Service Commandes
    |-- INSERT commande  --> PostgreSQL
    `-- PRODUCE événement --> Kafka
```

Deux systèmes indépendants ne participent pas automatiquement à une même transaction ACID. Si la base confirme l'écriture et que le processus tombe avant la publication, la commande existe mais son événement manque. Si l'événement est publié avant le commit SQL, les consommateurs peuvent recevoir un événement décrivant une opération finalement annulée.

| Ordre naïf | Fenêtre de défaillance | Conséquence |
| --- | --- | --- |
| Commit SQL, puis Kafka | Crash après commit | Événement perdu |
| Kafka, puis commit SQL | Rollback SQL | Événement fantôme |
| Kafka et SQL sans coordination | Échecs partiels | États divergents |

### 1.2 Le problème symétrique côté consommateur

Le consommateur modifie une base SQL, mais meurt avant de confirmer son offset Kafka. Kafka peut lui redélivrer le même événement. Sans mécanisme de déduplication, la modification métier est appliquée une deuxième fois : double débit, double crédit, inventaire incorrect ou statistiques gonflées.

**Les deux patrons répondent à deux risques distincts :**

- **Outbox** : éviter de perdre l'intention de publier un événement associé à une transaction métier validée.
- **Inbox** : empêcher qu'un même événement entraîne plusieurs fois les mêmes effets métier transactionnels.

Ils évitent normalement d'exiger une transaction distribuée 2PC entre Kafka et la base de données.

## 2. Principes et garanties

### 2.1 Atomicité locale

L'atomicité garantie est **celle de la base de données locale** : l'écriture métier et celle du registre Inbox/Outbox doivent être dans **la même transaction et la même portée transactionnelle**. Deux appels `save()` sans transaction commune ne suffisent pas.

### 2.2 Livraison et traitement

| Concept | Définition | Garantie habituelle |
| --- | --- | --- |
| At-most-once | Pas de répétition, perte possible | Non adapté aux effets critiques |
| At-least-once | Réessais jusqu'à livraison, doublons possibles | Outbox avec relais fiable |
| Effectively-once | Plusieurs livraisons, un seul effet métier observable | Inbox atomique + opérations couvertes |
| Exactly-once Kafka | Garanties Kafka dans un périmètre précis | Ne rend pas atomique une transaction SQL externe |

**Attention :** « exactement une fois » ne signifie pas « une seule livraison sur le réseau ». Les effets externes (courriel, paiement, API distante) exigent leurs propres protections d'idempotence.

### 2.3 Identités à distinguer

- `event_id` : identité stable d'un événement logique, inchangée pendant les réessais.
- `aggregate_id` : identité de l'entité métier (commande, inventaire, compte).
- `consumer_id` : identité logique et **stable** d'un traitement ou abonné; ce n'est ni le thread, ni l'instance de pod, ni nécessairement le `group.id` Kafka.
- `correlation_id` : relie les étapes d'un processus métier.
- `causation_id` : référence l'événement ou la commande à l'origine de l'événement.
- `event_type` : nature du fait métier, par exemple `InventoryReserved`.
- `status` : état technique du traitement, par exemple `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`.
- `schema_version` : version contractuelle du message.

## 3. Transactional Outbox

### 3.1 Intention

Une transaction métier écrit **ensemble** les données métier et un enregistrement Outbox. Un processus distinct publie ensuite cet enregistrement dans Kafka.

```text
             Transaction SQL unique
API ---> Service ----+----> UPDATE/INSERT métier
                     `----> INSERT outbox (PENDING)
                            |
                         COMMIT
                            |
                  Relay / CDC / polling
                            |
                         Kafka topic
```

### 3.2 Séquence nominale

1. Recevoir la commande et valider les règles métier.
2. Ouvrir une transaction SQL.
3. Modifier les données métier.
4. Créer un événement immuable avec `event_id`, clé d'agrégat et version.
5. Insérer l'événement dans `outbox_event`.
6. Confirmer la transaction SQL : les deux écritures sont durables.
7. Le relais découvre l'événement confirmé et l'envoie au broker.
8. Après accusé de réception durable du broker, le relais enregistre la réussite ou laisse CDC gérer la progression.

### 3.3 Invariants

- **Rollback SQL** : ni changement métier ni nouvelle ligne Outbox persistante.
- **Commit SQL** : l'intention de publier est durable, même si Kafka est indisponible.
- **Publication répétée possible** : un crash après l'ACK Kafka, avant la mise à jour de statut, provoque une nouvelle publication.
- **Aucune promesse de latence nulle** : le relais est asynchrone.
- **L'ordre n'est pas automatique** : il doit être conçu par agrégat et préservé par le relais et le partitionnement Kafka.

### 3.4 Ce que le patron ne garantit pas seul

Il ne garantit ni la réception par tous les abonnés, ni l'absence de doublons, ni l'ordre global entre agrégats, ni la disponibilité infinie des données en cas de panne permanente ou de mauvaise configuration. Il ne remplace pas la supervision et les procédures de reprise.

## 4. Transactional Inbox

### 4.1 Intention

L'Inbox est un registre durable des événements reçus ou traités. La variante **minimaliste** conserve l'identifiant de chaque événement appliqué; la variante **persistante avec état** stocke aussi la charge utile et permet de séparer ingestion et traitement.

```text
Kafka ---> Consumer ---> BEGIN SQL
                         |
                         +--> INSERT inbox (consumer_id, event_id)
                         |        UNIQUE (consumer_id, event_id)
                         +--> UPDATE données métier
                         |
                         `--> COMMIT SQL
                                |
                         Commit offset Kafka
```

### 4.2 Variante « processed events »

Le consommateur commence par tenter un `INSERT` protégé par une contrainte `UNIQUE`. Si la clé existe déjà, l'événement a été traité (selon le contrat de cette table) : aucun nouvel effet métier. Sinon, l'insertion et les changements métier sont confirmés ensemble.

**Ne pas implémenter** `SELECT existe ?` puis `INSERT` sans contrainte unique : deux consommateurs concurrents pourraient réussir le test initial.

### 4.3 Variante « durable inbox »

Une transaction courte persiste le message entrant et ses métadonnées. Un worker distinct traite les messages `RECEIVED`/`RETRY`, avec transitions atomiques, verrouillage et reprise. Cette variante facilite le découplage et le rejeu, mais requiert une **deuxième transaction** de traitement qui applique ensemble les effets métier et le statut final. Ne pas marquer `COMPLETED` avant le commit des effets métier.

### 4.4 Séquence nominale

1. Recevoir le message Kafka et valider son enveloppe.
2. Déterminer le `consumer_id` logique et l'`event_id` stable.
3. Démarrer une transaction SQL.
4. Tenter l'insertion de la clé Inbox unique.
5. Si la clé est nouvelle, appliquer les changements métier dans la même transaction.
6. Commit SQL; si la clé existait, ne pas réappliquer les effets.
7. Confirmer l'offset Kafka **après** le commit SQL, en respectant l'ordre de confirmation des offsets de la partition.

### 4.5 Cas important : échec après commit SQL

Le commit SQL est réussi, mais le processus s'arrête avant le commit d'offset. Kafka redélivre l'événement. La contrainte unique identifie le doublon; le consommateur ne répète pas les effets métier et peut confirmer l'offset après avoir constaté le traitement antérieur.

### 4.6 Portée du registre

Une clé `(consumer_id, event_id)` permet à plusieurs traitements indépendants de consommer légitimement le même événement. Si le contrat prévoit de retraiter un événement avec une nouvelle version de logique métier, utiliser un identifiant de traitement versionné ou une procédure de rejeu explicite, sans supprimer arbitrairement les protections.

## 5. Combiner Inbox et Outbox

Un service intermédiaire peut consommer `OrderCreated`, mettre à jour ses propres tables, puis produire `InventoryReserved`.

```text
Service A                  Kafka                Service B                  Kafka
---------                  -----                ---------                  -----
SQL métier + OUTBOX  --->  OrderCreated  --->  INBOX + SQL métier + OUTBOX ---> InventoryReserved
       (T1)                                        (T2)
```

Dans **T2**, le service B écrit atomiquement :

1. Le marqueur Inbox pour `OrderCreated`.
2. La réservation d'inventaire.
3. Le nouvel événement Outbox `InventoryReserved`.

Si T2 échoue, les trois écritures sont annulées. Si T2 réussit mais l'offset Kafka n'est pas confirmé, la redélivrance ne crée pas une seconde réservation ou un second événement Outbox. L'événement de sortie peut malgré tout être **publié** plusieurs fois par le relais.

**Invariant clé :** `INBOX + MÉTIER + OUTBOX` dans **une même transaction SQL locale**, puis ACK/commit Kafka après cette transaction.

## 6. Références

Sources de référence et lectures complémentaires :

1. Chris Richardson, *Transactional Outbox* : <https://microservices.io/patterns/data/transactional-outbox.html>
2. Chris Richardson, *Polling Publisher* : <https://microservices.io/patterns/data/polling-publisher.html>
3. Chris Richardson, *Transaction Log Tailing* : <https://microservices.io/patterns/data/transaction-log-tailing.html>
4. Microsoft Azure Architecture Center, *Idempotent Consumer Pattern* : <https://learn.microsoft.com/en-us/azure/architecture/patterns/idempotent-consumer>
5. Apache Kafka, *Design / Delivery Semantics* : <https://kafka.apache.org/documentation/#semantics>
6. Debezium, *Outbox Event Router* : <https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html>
7. PostgreSQL, *Explicit Locking* : <https://www.postgresql.org/docs/current/explicit-locking.html>
8. Spring for Apache Kafka, *Reference Documentation* : <https://docs.spring.io/spring-kafka/reference/>

---

**Conclusion.** L'Outbox garantit la durabilité de l'intention de publication après un commit métier; l'Inbox garantit l'idempotence des effets métier transactionnels face aux redélivrances. Leur combinaison permet de construire des chaînes EDA résilientes, à condition de traiter explicitement l'ordre, les transactions, les réessais, les effets externes, l'exploitation et le rejeu.
