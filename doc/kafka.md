# 📘 Guide d'Ingénierie Kafka

## Stream Processing, Résilience EDA & Déploiement EKS

> **Public cible** : Développeurs Java/Spring Boot opérant des consumers Kafka dans des environnements conteneurisés (Kubernetes / EKS).

---

## 🗂 Table des matières

1. [Fondamentaux Kafka](#1-fondamentaux-kafka)
2. [Architecture & Design de Topics](#2-architecture--design-de-topics)
3. [Consumer Groups & Partitionnement](#3-consumer-groups--partitionnement)
4. [Cycle de Vie & Opérations](#4-cycle-de-vie--opérations)
5. [Fiabilité & Sémantiques de Livraison](#5-fiabilité--sémantiques-de-livraison)
6. [Patterns de Déduplication](#6-patterns-de-déduplication)
7. [Transactions Kafka](#7-transactions-kafka)
8. [Patterns de Persistance](#8-patterns-de-persistance)
9. [Scaling dans EKS](#9-scaling-dans-eks)
10. [Gouvernance & Observabilité](#10-gouvernance--observabilité)
11. [KafkaSandbox – Tests](#11-kafkasandbox--tests)
12. [Configuration de Référence](#12-configuration-de-référence)

---

## 1. Fondamentaux Kafka

### Architecture générale

Kafka est une plateforme de streaming distribuée organisée autour de trois rôles :

- **Producers** : écrivent des messages vers des topics (reçoivent optionnellement un ACK/NACK).
- **Brokers** : reçoivent, stockent et servent les messages. Un cluster de brokers constitue le cœur de Kafka.
- **Consumers** : lisent les messages par polling périodique. Plusieurs consumers peuvent lire le même topic indépendamment.

La clé du découplage Kafka réside dans le fait que producers et consumers n'ont besoin que de s'accorder sur le format de données — ils n'interagissent jamais directement.

### Le record Kafka

```
┌──────────────┐
│   Headers    │  ← Métadonnées optionnelles (key/value)
├──────────────┤
│     Key      │  ← Détermine la partition cible
├──────────────┤
│    Value     │  ← Données métier
├──────────────┤
│  Timestamp   │  ← Heure de création ou d'ingestion
└──────────────┘
```

### Topics, Partitions & Rétention

- Un **topic** est une représentation logique qui s'étend sur producers, brokers et consumers.
- Chaque topic est divisé en **partitions** — l'unité fondamentale de parallélisme.
- L'ordre des messages est garanti **uniquement au sein d'une partition**.
- La **rétention** est configurable par topic (défaut : 1 semaine). Les données sont supprimées par segment.
- Les **topics compactés** ne conservent que le dernier message par clé — utiles pour reconstruire un état après crash.

### Réplication & Leadership

Chaque partition possède un **leader** et des **followers**. Les clients (producers/consumers) n'interagissent qu'avec le leader. Si le leader tombe, Kafka réélit un follower automatiquement. Le paramètre `acks` du producer contrôle le niveau de durabilité :

| `acks` | Comportement | Risque |
|--------|-------------|--------|
| `0` | Pas de confirmation | Perte possible |
| `1` | Confirmation du leader uniquement | Perte si le leader crash avant réplication |
| `all` | Confirmation de tous les ISRs | Garantie maximale |

---

## 2. Architecture & Design de Topics

### Fat Pipe vs Thin Pipe

Le choix du design de topics est l'une des décisions architecturales les plus structurantes.

#### Fat Pipe (Topic unique)

Tous les types d'événements transitent par un seul topic.

**Problèmes :**
- Chaque service filtre des messages non pertinents → gaspillage CPU, réseau, I/O.
- La validation de schéma est complexe (multiples schémas non liés sur un même topic).
- Impossible de définir une clé de partition cohérente pour des types d'événements hétérogènes, ce qui conduit à des déséquilibres (hot partitions) et à la perte des garanties d'ordre.
- Le système devient difficile à comprendre, tester et faire évoluer.

#### Thin Pipe (Topics dédiés)

Chaque type d'événement a son propre topic.

**Avantages :**
- Respecte le principe de responsabilité unique des microservices.
- Renforce les frontières de domaine.
- Clé de partition naturelle et cohérente.
- Gouvernance et ACLs simplifiés.

**Limites :**
- Multiplier les topics augmente les métadonnées du cluster (guideline : ~4 000 partitions par broker).
- L'ordre inter-events de domaines différents n'est plus garanti.

#### La voie du milieu (recommandée)

En pratique, regrouper les événements par **agrégat ou entité métier** est la stratégie la plus efficace. Tous les événements liés à la même entité (ex. `Order`) partagent un topic et une clé de partition commune (`orderId`), garantissant l'ordre au sein de cet agrégat sans coupler des domaines non liés.

La **sensibilité des données** peut également motiver des topics distincts (ACLs, chiffrement sélectif).

### Clé de partition & Ordre

```
partition = hash(key) % nb_partitions
```

- Tous les messages avec la même clé vont dans la même partition → traités séquentiellement par le même consumer.
- Sans clé → distribution aléatoire ou round-robin → aucune garantie d'ordre.

> ⚠️ **Hot partition** : si certaines clés génèrent un volume disproportionné, une partition devient un goulot d'étranglement. Mitigation : sharding secondaire (`clientId % N`).

---

## 3. Consumer Groups & Partitionnement

### Principe

Un **consumer group** regroupe des instances consommatrices identiques partageant le même `group.id`. Kafka répartit automatiquement les partitions entre les membres du groupe.

**Règle fondamentale :**

```
Partitions actives = min(nb_consumers, nb_partitions)
```

- Si `consumers > partitions` : les consumers excédentaires restent **inactifs**.
- Si `consumers < partitions` : chaque consumer traite plusieurs partitions.
- Optimum : `nb_consumers × threads_par_consumer = nb_partitions` (ou diviseur).

### Concurrency Spring Kafka

```java
// Option 1 : directement sur le listener
@KafkaListener(topics = "orders", concurrency = "3")
public void listen(String message) { ... }

// Option 2 : via la factory
factory.setConcurrency(3);
```

Chaque unité de concurrency est un consumer indépendant dans le group — elle consomme une ou plusieurs partitions.

### Consumer Group Rebalance

Un **rebalance** redistribue les partitions entre les membres du groupe. Il est déclenché par :
- L'arrivée ou le départ d'un consumer (`JoinGroup` / `LeaveGroup`).
- Le dépassement de `max.poll.interval.ms` (consumer considéré mort).
- La perte de heartbeat au-delà de `session.timeout.ms`.

#### Paramètres clés

| Paramètre | Défaut | Rôle |
|-----------|--------|------|
| `session.timeout.ms` | 45s | Délai de détection de la mort d'un consumer (heartbeat) |
| `heartbeat.interval.ms` | 3s | Fréquence des heartbeats (recommandé : ≤ 1/3 de session.timeout) |
| `max.poll.interval.ms` | 5min | Délai max entre deux `poll()` avant éviction |

#### Stratégies de rebalance

**Eager (défaut)** : tout traitement s'arrête pendant la redistribution — toutes les partitions sont révoquées puis réassignées.

**Cooperative Sticky (recommandée en prod)** : seules les partitions qui changent de propriétaire sont temporairement révoquées. Les autres consumers continuent de traiter sans interruption. Se configure via `partition.assignment.strategy=CooperativeStickyAssignor`.

#### Static Group Membership

En configurant un `group.instance.id` unique par consumer (ex. le nom du pod), Kafka reconnaît un consumer qui redémarre sans déclencher de rebalance — à condition qu'il rejoigne avant l'expiration de `session.timeout.ms`.

```yaml
spring:
  kafka:
    consumer:
      properties:
        group.instance.id: ${POD_NAME}
```

Particulièrement utile en Kubernetes pour les rolling restarts.

#### Rebalance Storm

Si des services lents dépassent régulièrement `max.poll.interval.ms`, ils sont évincés puis rejoignent immédiatement, déclenchant un rebalance en chaîne. Remèdes : augmenter `max.poll.interval.ms`, réduire `max.poll.records`, utiliser le Cooperative Sticky Assignor.

---

## 4. Cycle de Vie & Opérations

### Graceful Shutdown en EKS

Les pods Kubernetes sont éphémères. Un arrêt brutal provoque :
- Un rebalance non planifié.
- Des offsets non commités → messages retraités en double.
- Des messages en cours de traitement abandonnés.

#### Configuration Kubernetes

```yaml
spec:
  terminationGracePeriodSeconds: 60   # ≥ durée max d'un batch
  containers:
    - name: kafka-app
      lifecycle:
        preStop:
          exec:
            command: ["sh", "-c", "sleep 10"]  # laisser le temps au rebalance
```

#### Configuration Spring Boot

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 60s

  kafka:
    listener:
      ack-mode: MANUAL_IMMEDIATE
    consumer:
      enable-auto-commit: false
      max-poll-records: 50
```

#### Séquence d'arrêt propre

```
SIGTERM (Kubernetes)
    → Spring : ContextClosedEvent
    → KafkaListenerContainer.stop()
    → Plus de poll()
    → Batch en cours terminé
    → ack.acknowledge()
    → Offsets commités
    → Consumer fermé (leaveGroup → rebalance)
```

#### Pattern : ShutdownFlag

```java
@Component
public class ShutdownFlag {
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @EventListener
    public void onShutdown(ContextClosedEvent e) {
        shuttingDown.set(true);
    }

    public boolean isShuttingDown() { return shuttingDown.get(); }
}

@KafkaListener(topics = "orders")
public void listen(String msg, Acknowledgment ack) {
    if (shutdownFlag.isShuttingDown()) {
        // Pas d'ACK → Kafka redélivrera ce message au prochain démarrage
        return;
    }
    process(msg);
    ack.acknowledge();
}
```

#### SmartLifecycle (consumer manuel)

```java
@Component
public class KafkaConsumerLifecycle implements SmartLifecycle {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private KafkaConsumer<String, String> consumer;

    @Override
    public void start() {
        running.set(true);
        consumer = createConsumer();
        // démarrer la boucle de poll dans un thread dédié
    }

    @Override
    public void stop(Runnable callback) {
        running.set(false);
        consumer.wakeup();   // interrompt poll() avec WakeupException
        callback.run();
    }

    @Override
    public boolean isRunning() { return running.get(); }

    @Override
    public int getPhase() { return 0; }
}
```

> `consumer.wakeup()` est la méthode officielle Kafka pour interrompre un `poll()` bloquant depuis un autre thread. Elle déclenche une `WakeupException` dans la boucle de polling.

### Pause vs Stop

| Méthode | Effet sur le consumer | Impact Kafka |
|---------|-----------------------|-------------|
| `pause()` | Arrête de demander de nouveaux messages | Le consumer reste dans le groupe (pas de rebalance) |
| `stop()` | Arrête complètement le container | Déclenche un rebalance immédiat |

```java
// Pause via le registry
registry.getListenerContainer("myListenerId").pause();
registry.getListenerContainer("myListenerId").resume();
```

### ConsumerStoppedEvent (Spring Kafka 2.9+)

```java
@EventListener
public void onConsumerStopped(ConsumerStoppedEvent event) {
    // Hook moderne pour réagir à l'arrêt d'un container
}
```

---

## 5. Fiabilité & Sémantiques de Livraison

### At-Most-Once

Les offsets sont commités **avant** le traitement. En cas d'échec, le message est perdu mais jamais dupliqué. Cas d'usage rare.

### At-Least-Once (défaut)

Les offsets sont commités **après** le traitement. En cas d'échec, le message est redélivré. Des doublons sont possibles — le consumer doit être idempotent.

```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false
    listener:
      ack-mode: MANUAL_IMMEDIATE
```

#### Modes d'ACK

| Mode | Comportement |
|------|-------------|
| `MANUAL` | Commit groupé à la fin du batch (`poll`) |
| `MANUAL_IMMEDIATE` | Commit immédiat à l'appel de `acknowledge()` |
| `BATCH` | Commit automatique après chaque batch |

`MANUAL_IMMEDIATE` est recommandé pour une meilleure granularité et visibilité des commits.

### Exactly-Once (EOS)

Kafka garantit que le triplet **consommer + traiter + produire** se produit exactement une fois du point de vue des consumers **transaction-aware** (`isolation.level=read_committed`).

> ⚠️ Les opérations annexes (appels REST, écritures DB) peuvent toujours se reproduire plusieurs fois. EOS garantit uniquement qu'aucun message dupliqué ne sera visible par les consumers en aval.

### Gestion des erreurs

#### nack() : retry différé

```java
acknowledgment.nack(Duration.ofSeconds(5));
```

Utile pour les erreurs transitoires (service indisponible). Le message est remis en file après le délai.

#### DefaultErrorHandler + DLQ

```java
DefaultErrorHandler errorHandler = new DefaultErrorHandler(
    new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
    ),
    new FixedBackOff(1000L, 3)
);
errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
```

Les messages non récupérables après N retries sont routés vers un **Dead Letter Topic (DLT)** pour analyse manuelle.

### Batch Consume : comportement en cas d'erreur

| Scénario | Comportement |
|----------|-------------|
| Happy path | Offsets écrits pour le dernier message du batch |
| Consumer meurt mid-batch | Pas de commit → tout le batch est redélivré |
| Exception propagée au framework | Offsets écrits jusqu'au message précédent → le message fautif est retempté |
| Exception capturée dans le listener | Message considéré traité → passage au suivant |

---

## 6. Patterns de Déduplication

Les messages dupliqués sont inévitables en at-least-once. Le tableau suivant résume les garanties offertes par chaque pattern selon le scénario d'échec :

| Pattern | Service meurt après POST+INSERT | Service meurt après PRODUCE | DB commité, service meurt | Poll timeout |
|---------|--------------------------------|---------------------------|--------------------------|-------------|
| Aucun | POST + INSERT dupliqués | POST + INSERT + PRODUCE | POST + INSERT + PRODUCE | Idem |
| Idempotent Consumer | POST dupliqué | POST + PRODUCE | Aucun | Aucun |
| Transactional Outbox | POST dupliqué | N/A | POST + INSERT + PRODUCE | Idem |
| IC + Transactional Outbox | POST dupliqué | N/A | Aucun | Aucun |
| Kafka Transactions | POST dupliqué | POST + INSERT | N/A | POST + INSERT |

### Pattern 1 : Idempotent Consumer

Stocker l'ID de chaque message traité en base, dans la **même transaction** que les opérations métier.

```sql
CREATE TABLE processed_events (
    event_id VARCHAR(36) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);
```

```java
@Transactional
public void process(Event event) {
    // saveAndFlush provoque un verrou immédiat sur la ligne
    // → le second consumer duplicate attendra, puis échouera sur violation de contrainte
    processedEventRepository.saveAndFlush(new ProcessedEvent(event.getId()));

    // Logique métier
    orderService.save(event);
}
```

> Utiliser `saveAndFlush()` (et non `save()`) pour acquérir le verrou en base immédiatement, évitant le traitement en parallèle de doublons.

### Pattern 2 : Transactional Outbox

Les messages sortants sont écrits dans une table `outbox` **dans la même transaction DB** que les données métier. Un agent CDC (Debezium) publie ensuite ces entrées vers Kafka.

```sql
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100),
    payload JSONB,
    published BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);
```

```java
@Transactional
public void consume(Event event, Acknowledgment ack) {
    orderService.save(event);              // 1. Écriture métier
    outboxRepository.save(OutboxEvent.from(event)); // 2. Outbox (atomique)
    ack.acknowledge();                     // 3. Commit offset
}
// CDC (Debezium) publie l'outbox → Kafka topic sortant
```

### Recommandation

Combiner **Idempotent Consumer + Transactional Outbox** est l'approche gold standard. Le seul risque résiduel est un appel REST dupliqué — qui doit être géré côté service tiers (idempotence).

> ⚠️ **Ne pas combiner** Idempotent Consumer avec Kafka Transactions : la non-atomicité entre transactions DB et Kafka crée des scenarios de perte de données irréductibles.

---

## 7. Transactions Kafka

### Idempotent Producer

Empêche les doublons causés par les retries du producer (réseau, timeout).

```yaml
spring:
  kafka:
    producer:
      acks: all
      retries: 2147483647  # active implicitement l'idempotence
```

Kafka utilise un `producerId` + `sequenceNumber` par partition pour rejeter les doublons exacts au niveau du broker.

> L'idempotence s'applique **producer → broker** uniquement. Elle ne protège pas contre les re-consommations côté consumer.

### Transactions Kafka (EOS complet)

```java
// Pipeline consume → produce EOS
producer.initTransactions();
producer.beginTransaction();
try {
    producer.send(new ProducerRecord<>("output-topic", key, value));
    producer.sendOffsetsToTransaction(offsets, consumer.groupMetadata());
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

Avec Spring Kafka :

```java
@Bean
public KafkaTransactionManager<String, String> kafkaTransactionManager(
        ProducerFactory<String, String> pf) {
    return new KafkaTransactionManager<>(pf);
}

@Transactional("kafkaTransactionManager")
@KafkaListener(topics = "inbound-topic")
public void listen(String msg) {
    kafkaTemplate.send("outbound-topic", msg);
}
```

### transactional.id & Kubernetes

Le `transactional.id` identifie un **producer logique**. Il doit être :
- **Stable** pendant toute la durée de vie du producer.
- **Unique** parmi les producers actifs simultanément.

En multi-pods EKS, utiliser le nom du pod pour garantir l'unicité :

```yaml
spring:
  kafka:
    producer:
      transaction-id-prefix: ${POD_NAME}-tx-
```

```yaml
env:
- name: POD_NAME
  valueFrom:
    fieldRef:
      fieldPath: metadata.name
```

> Le `transactional.id` peut changer entre redémarrages de pods (pod mort → nouvel ID). Kafka aborte les transactions orphelines automatiquement.

### ChainedKafkaTransactionManager

Synchronise les transactions Kafka et JPA. Si la DB échoue, l'offset n'est pas commité.

```java
@Bean
public PlatformTransactionManager chainedTransactionManager(
        KafkaTransactionManager<String, String> kafkaTM,
        JpaTransactionManager jpaTM) {
    return new ChainedKafkaTransactionManager<>(kafkaTM, jpaTM);
}
```

> ⚠️ La synchronisation via two-phase commit n'est pas atomique à 100%. Ce pattern ne remplace pas le Transactional Outbox pour les garanties maximales.

### Pattern Medallion (Bronze → Argent → Or) avec EOS

```
Topic Bronze  →  Pipeline EOS  →  Topic Argent  →  Pipeline EOS  →  Topic Or
             (consume+produce             (consume+produce
              atomique)                   atomique)
```

Chaque étape encapsule le `consume + transform + produce` dans une transaction Kafka, garantissant qu'aucune donnée ne se perd ou ne se duplique entre les couches.

---

## 8. Patterns de Persistance

### Locking pour la concurrence

**Verrouillage optimiste** : ajouter un champ `@Version` sur l'entité. Spring/Hibernate rejette les mises à jour concurrentes avec une `OptimisticLockException`.

```java
@Entity
public class Order {
    @Version
    private Long version;
    // ...
}
```

**Verrouillage pessimiste** : acquérir un verrou DB avant traitement.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT o FROM Order o WHERE o.id = :id")
Optional<Order> findByIdForUpdate(@Param("id") Long id);
```

### Gestion manuelle des transactions Spring

```java
// Option 1 : @Transactional (standard, 90% des cas)
@Transactional
public void process(Event event) { ... }

// Option 2 : TransactionTemplate (blocs précis)
transactionTemplate.execute(status -> {
    // opérations atomiques
    return null;
});

// Option 3 : PlatformTransactionManager (contrôle ultra-fin)
TransactionStatus status = transactionManager.getTransaction(def);
try {
    // opérations
    transactionManager.commit(status);
} catch (Exception ex) {
    transactionManager.rollback(status);
    throw ex;
}
```

---

## 9. Scaling dans EKS

### Contraintes de scalabilité

Le nombre de pods actifs **ne peut jamais dépasser le nombre de partitions**. Planifier le nombre de partitions en anticipant le scaling horizontal maximal souhaité.

```
Pods actifs max = nb_partitions
Total consumers = nb_pods × concurrency_par_pod ≤ nb_partitions
```

### KEDA (Kubernetes Event-Driven Autoscaling)

KEDA est l'approche recommandée pour scaler les consumers Kafka en EKS. Il s'appuie sur le **consumer lag** plutôt que sur CPU/RAM.

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: kafka-consumer-scaled
spec:
  scaleTargetRef:
    name: kafka-consumer
  minReplicaCount: 1
  maxReplicaCount: 10
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: "kafka:9092"
        consumerGroup: "my-consumer-group"
        topic: "orders"
        lagThreshold: "100"
```

- Lag = 0 → KEDA réduit les pods (peut descendre à 0 avec `minReplicaCount: 0`).
- Lag > seuil → KEDA ajoute des pods.
- Réactif toutes les ~2 secondes.

### KEDA vs Karpenter

| | KEDA | Karpenter |
|-|------|-----------|
| Rôle | Autoscaler de **pods** | Autoscaler de **nodes EC2** |
| Déclencheur | Lag Kafka, métriques métier | Besoins de scheduling des pods |
| Scale-to-zero | ✅ Oui | ❌ Non applicable |
| Usage | Applications event-driven | Optimisation coût/capacité cluster |

Ces deux outils sont **complémentaires** : KEDA décide du nombre de pods, Karpenter provisionne les nodes pour les accueillir.

### Manifest EKS de référence

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kafka-consumer
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    spec:
      terminationGracePeriodSeconds: 60
      containers:
        - name: app
          image: myrepo/kafka-consumer:latest
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 10"]
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 3
            periodSeconds: 3
            failureThreshold: 1
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 10
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                topologyKey: kubernetes.io/hostname
```

> La `readinessProbe` rapide est critique : dès qu'un shutdown commence, le pod est retiré du Service Kubernetes → Kafka arrête d'envoyer de nouvelles partitions.

---

## 10. Gouvernance & Observabilité

### Health Indicators Spring Boot Actuator

```java
@Component
public class KafkaReadinessHealthIndicator implements HealthIndicator {

    private final KafkaConsumerLifecycle lifecycle;

    @Override
    public Health health() {
        if (lifecycle.isShutdownInProgress()) {
            return Health.down()
                    .withDetail("kafka", "shutdown in progress")
                    .build();
        }
        return Health.up().build();
    }
}
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, prometheus
  endpoint:
    health:
      probes:
        enabled: true   # active /actuator/health/liveness et /readiness
      show-details: always
```

### Métriques Kafka clés (Prometheus)

| Métrique | Description |
|----------|-------------|
| `kafka_consumer_records_lag_max` | 🔥 Lag max — indicateur de santé principal |
| `kafka_consumer_records_consumed_total` | Throughput de consommation |
| `kafka_consumer_rebalance_total` | Nombre de rebalances |
| `kafka_listener_seconds_max` | Durée max de traitement |
| `kafka_consumer_poll_time_max` | Durée max des polls |

### Data Mesh & Data Fabric

- **Data Mesh** : modèle organisationnel où chaque domaine est propriétaire de ses données (Data-as-a-Product). Kafka sert de "protocole de communication" entre domaines.
- **Data Fabric** : couche technique qui automatise l'accès et l'intégration des données à travers l'entreprise.

### Corroboration des données

La cohérence des données dans une EDA n'est pas garantie par défaut. Elle doit être explicitement architecturée.

| Technique | Objectif | Coût |
|-----------|----------|------|
| **Event Replay** | Retraiter l'historique pour comparer l'état attendu vs réel | Élevé sur grand volume |
| **Snapshots périodiques** | Comparer l'état DB avec une reconstruction à partir des événements | Faible, mais différé |
| **Checksums / Hashes** | Détecter rapidement toute divergence d'état | Très faible |
| **Événements de contrôle** | Balises de validation opérationnelle (ex. `EndOfDayProcessed`) | Simple |
| **Shadow Consumers** | Consumer parallèle qui relit sans commit pour valider une nouvelle logique | Infrastructure additionnelle |
| **Réconciliation périodique** | Comparaison DB ↔ flux Kafka historique | Fiable, audit complet |

**Stratégie recommandée en production :**

```
Détection rapide     → Checksums
Audit & conformité   → Replay + Snapshots
Confiance opéra.     → Événements de contrôle
Migration / Refacto  → Shadow Consumers
```

---

## 11. KafkaSandbox – Tests

Un utilitaire léger au-dessus de `EmbeddedKafka` pour les tests unitaires et d'intégration.

### Configuration

```java
KafkaSandbox sandbox = KafkaSandbox.builder()
    .autoStart(true)
    .addTopic("orders", 3, (short) 1,
        StringSerializer.class, StringSerializer.class,
        StringDeserializer.class, StringDeserializer.class)
    .addTransactionalTopic("payments", 3, (short) 1, "payments-tx-1",
        StringSerializer.class, StringSerializer.class,
        StringDeserializer.class, StringDeserializer.class)
    .build();
```

### Production (non-transactionnelle)

```java
KafkaTemplate<Object, Object> orders = sandbox.getKafkaTemplate("orders");
orders.send("orders", "order-1", "CREATED");
```

### Production transactionnelle

```java
// Via KafkaTemplate
sandbox.getKafkaTemplate("payments").executeInTransaction(kt -> {
    kt.send("payments", "p-1", "INIT");
    kt.send("payments", "p-1", "AUTHORIZED");
    return null;
});

// Via Producer natif (contrôle bas niveau)
try (ProducerTransaction<String, String> tx = sandbox.borrowTransactionalProducer("payments")) {
    tx.producer().send(new ProducerRecord<>("payments", "p-1", "CAPTURED"));
    tx.commit();
}
```

### Consommation

```java
sandbox.setupMessageListener("orders",
    (AcknowledgingMessageListener<String, String>) (record, ack) -> {
        System.out.println("[ORDERS] " + record.value());
        ack.acknowledge();
    }
);
```

### TransactionalProducerPool

Pour les tests multi-thread nécessitant des transactions parallèles :

```java
// 1 producer transactionnel par thread — jamais partagé
sandbox.createTransactionalProducerPool("payments", 4);

ExecutorService pool = Executors.newFixedThreadPool(4);
pool.submit(() -> {
    try (var tx = sandbox.borrowTransactionalProducer("payments")) {
        tx.producer().send(...);
        tx.commit();
    }
});
```

> **Règle d'or** : un `KafkaProducer` est thread-safe, mais **une transaction ne l'est pas**. Un seul thread à la fois doit posséder un producer transactionnel.

---

## 12. Configuration de Référence

### application.yml (production EKS)

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 60s

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

    consumer:
      enable-auto-commit: false
      max-poll-records: 50
      isolation-level: read_committed
      properties:
        max.poll.interval.ms: 300000
        heartbeat.interval.ms: 1000
        session.timeout.ms: 10000

    listener:
      ack-mode: MANUAL_IMMEDIATE
      type: single          # ou 'batch'
      concurrency: 1
      no-poll-throttle: 100

    producer:
      acks: all
      retries: 2147483647
      transaction-id-prefix: ${POD_NAME}-tx-

management:
  endpoints:
    web:
      exposure:
        include: health, prometheus
  endpoint:
    health:
      probes:
        enabled: true
  metrics:
    tags:
      application: kafka-consumer
```

### Tableau de référence rapide

| Paramètre | Valeur recommandée | Raison |
|-----------|-------------------|--------|
| `enable.auto.commit` | `false` | Contrôle manuel des offsets |
| `ack-mode` | `MANUAL_IMMEDIATE` | Commit précis et réactif |
| `isolation.level` | `read_committed` | Ignorer les messages non commités |
| `max.poll.records` | 10–100 | Batches maîtrisables au shutdown |
| `max.poll.interval.ms` | ≥ 300 000 ms | Éviter les rebalances sur traitements longs |
| `heartbeat.interval.ms` | ~1 000 ms | Détection rapide des pannes |
| `session.timeout.ms` | 10 000 ms | Rebalance rapide post-crash |
| `acks` (producer) | `all` | Durabilité maximale |
| `terminationGracePeriodSeconds` | ≥ 30s | Temps pour terminer le batch en cours |
| `partition.assignment.strategy` | `CooperativeStickyAssignor` | Rebalances moins disruptifs |

### Anti-patterns à éviter

| ❌ Anti-pattern | ✅ Correction |
|----------------|--------------|
| `enable.auto.commit=true` | Désactiver, utiliser ACK manuel |
| Commit avant traitement | Toujours commiter **après** traitement |
| UUID régénéré à chaque démarrage pour `transactional.id` | UUID stable par instance ou `${POD_NAME}` |
| Partager un producer transactionnel entre threads | 1 producer transactionnel = 1 thread |
| `terminationGracePeriodSeconds` < durée d'un batch | Toujours supérieur au temps de traitement max |
| ACK en cas de shutdown détecté | `return` sans ACK → redélivrance garantie |
| Combiner IC + Kafka Transactions | Utiliser IC + Transactional Outbox |
| DLQ gérée dans le listener | Déléguer à `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` |

---

*Guide synthétisé à partir de la documentation interne et des meilleures pratiques Kafka / Spring Boot / AWS EKS.*