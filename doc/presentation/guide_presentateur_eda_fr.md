# Guide du présentateur - Projet EDA

## Parcours

Présentation française de 44 diapositives : 40 diapositives de parcours et 4 annexes.

Base : les 17 fichiers Markdown de doc/fr/ dans le ZIP fourni, ainsi que les captures et rapports qu'ils référencent. Le modèle visuel reprend le PowerPoint fourni avec la demande.

La présentation ne constitue pas une nouvelle exécution du projet ni une vérification des corrections postérieures à l'archive. Les chiffres des rapports sont explicitement ceux des exemples documentaires. Les patrons de production sont distingués du comportement du sandbox.

## Utilisation

Préparer la version du sandbox utilisée pour le cours. Les valeurs de concurrence par défaut et certaines formulations sur le rejeu varient entre les chapitres de l'archive : les diapositives évitent d'en faire une configuration universelle. Leurs limites sont signalées dans les notes des diapositives concernées. Les captures de l'application restent dans la langue de leur version source.

## 01 - Guide d'ingénierie Kafka

Du premier événement à la résilience EDA

### Notes

Intention : partir d'une requête d'inventaire concrète, puis introduire les mécanismes techniques au moment où une question de fiabilité les rend nécessaires. Ce support suit la documentation française du projet. Il ne présente ni une certification de production ni une nouvelle exécution de la démonstration. Les captures et les rapports sont ceux de l'archive. Le modèle fourni est repris pour les couleurs, la typographie, le logo et la date. Les annexes servent de référence sans interrompre le parcours principal.

### Sources

- doc/fr/LISEZ_MOI.md, lignes 5-25 et 43-70
- Template fourni : org-rd-fullstack-springboot-eda.pptx, diapositive 1 (identité visuelle, logo et date)

## 02 - Six étapes pour construire le raisonnement

Un même fil conducteur : publier une requête, produire un effet métier, puis vérifier cet effet.

### Notes

Présenter ce plan comme une progression, pas comme une liste de produits. On commence avec l'application et les données. Kafka arrive ensuite pour comprendre où passent les messages. Les défaillances justifient les transactions, l'idempotence et les patrons de persistance. Flink devient un autre modèle d'exécution à comparer. La distribution conduit aux opérations et à l'élasticité. Enfin, l'observabilité, la gouvernance, la corroboration et le choix ETL/SOA/EDA ferment la boucle. Les notes contiennent les références de chaque diapositive.

### Sources

- doc/fr/LISEZ_MOI.md, lignes 51-70 (index thématique)
- doc/fr/conclusion_etl_soa_eda_tco_roi.md, lignes 145-156

## 03 - Publier un message ne suffit pas à construire une EDA

EDA : architecture événementielle. Le transport rend l'échange possible; il ne garantit pas, seul, la fiabilité des effets.

### Notes

Utiliser l'iceberg fourni dans le guide comme métaphore de la complexité. La partie visible est la publication et la consommation. La partie moins visible comprend l'ordre, les défaillances partielles, les doublons, les transactions, l'exploitation et la vérification des données. Ne pas présenter cette image comme une mesure de coût ou de volume. Elle introduit l'idée centrale de la conclusion : la complexité ne disparaît pas, elle se déplace. Question d'ouverture : qu'est-ce qui permet de dire qu'une vente a réellement été appliquée ?

### Sources

- doc/fr/LISEZ_MOI.md, lignes 43-49
- doc/fr/conclusion_etl_soa_eda_tco_roi.md, lignes 50-71 et 145-156
- doc/asserts/eda-shark-iceberg.png (illustration du guide)

## 04 - Une application autonome pour observer les compromis

Le frontend statique Nuxt et le backend Spring Boot sont regroupés dans un seul artefact déployable.

### Notes

Décrire d'abord les rôles, sans entrer dans les API. Nuxt fournit l'interface et Spring Boot expose les services BFF tout en servant les ressources statiques. Kafka, Flink et Hazelcast sont embarqués pour faciliter l'expérimentation; la base est HSQLDB. La documentation indique explicitement que ce projet est éducatif. L'empaquetage unifié n'est pas une recommandation universelle : il répond ici à un objectif de simplicité locale. Les composants d'infrastructure devraient généralement devenir des services externes en production. La séparation sera reprise plus loin.

### Sources

- doc/fr/LISEZ_MOI.md, lignes 5-25
- doc/fr/guides_sandbox.md, lignes 17-35

## 05 - Une requête relie une personne, un produit et un stock

Le traitement doit faire évoluer le solde, l'inventaire et le résultat de la requête de façon cohérente.

### Notes

Conserver la terminologie du projet : CREDIT correspond à une vente et retire du stock; DEBIT ajoute du stock et recrédite le client. Le sens peut sembler contre-intuitif, d'où la nécessité de le préciser. REQUEST est la demande à traiter, PERSON porte le solde, PRODUCT le prix et INVENTORY la quantité. Les résultats PENDING, BACK_ORDER, EXECUTED et ERROR seront réutilisés dans les explications d'idempotence et de corroboration. L'invariant de stock non négatif est un objectif métier, pas une garantie déjà assurée dans tous les modes du sandbox.

### Sources

- doc/fr/base_de_donnees.md, sections PERSON, PRODUCT, INVENTORY, REQUEST et lignes 230-259
- doc/fr/verification_donnees.md, lignes 130-138

## 06 - Le tableau des opérations devient le poste d'expérimentation

Changer une option, observer les effets, puis expliquer le résultat plutôt que seulement le constater.

### Notes

Montrer les contrôles présents dans la capture du projet : activation des clés, latence artificielle, rejeu, chemin Flink, pause et actions de démarrage/réinitialisation. La capture originale est en anglais; les explications du support sont en français. Pour comparer deux essais, conserver le même jeu de données et ne changer qu'une variable. Les effets attendus sont ceux décrits dans le guide, pas une nouvelle mesure. Ne pas réinitialiser un lot encore en cours lors de la démonstration. Préparer et vérifier la version utilisée avant le cours.

### Sources

- doc/fr/architecture_et_conception_des_topics.md, lignes 165-193
- doc/fr/guides_flink.md, lignes 24-64
- doc/asserts/operations_dashboard.png (capture fournie, recadrée)

## 07 - Suivre une requête du bouton jusqu'à la base

Chaque composant porte une responsabilité distincte; les frontières de validation viendront ensuite.

### Notes

Raconter le parcours sans déployer tous les détails transactionnels. L'interface soumet l'action; PipelineSrv publie les requêtes dans le topic du chemin direct; le listener reçoit chaque enregistrement; PipelineSrv.handle coordonne puis ProcessorSrv.process applique le travail métier. Le listener accuse réception après le retour du traitement transactionnel. Les deux modes Kafka et Flink partageront ce cœur métier. Demander aux étudiants où une panne pourrait se produire : chaque flèche ouvre une question que les prochaines sections vont traiter.

### Sources

- doc/fr/guides_flink.md, lignes 24-35
- doc/fr/reconnaissance_et_idempotence_consommateur.md, lignes 210-216
- doc/fr/base_de_donnees.md, lignes 260-300

## 08 - Quatre mots pour lire un flux Kafka

Le topic organise les messages; la partition donne l'ordre; l'offset repère une position; le groupe partage le travail.

### Notes

Introduire un vocabulaire minimal. Un topic contient des partitions. Chaque partition est un journal ordonné. L'offset est la position d'un enregistrement dans cette partition; l'offset validé du groupe indique sa reprise. Dans un groupe, une partition n'est attribuée qu'à un consommateur actif à la fois. Le dessin simplifie volontairement le nombre de partitions et de consommateurs; il ne représente pas les valeurs de configuration de l'archive. Ne pas parler encore de garantie exactly-once : l'ordre et le suivi de position ne sont pas une garantie sur les effets SQL.

### Sources

- doc/fr/architecture_et_conception_des_topics.md, lignes 19-38 et 93-102
- doc/fr/semantiques_livraison_et_fiabilite.md, lignes 55-69

## 09 - La clé Kafka doit exprimer l'ordre métier recherché

Dans le projet, key = productId rapproche l'ordre des messages de l'invariant d'inventaire.

### Notes

Le guide utilise productId comme clé de partition. À nombre de partitions inchangé, les requêtes d'un même produit rejoignent la même partition. Dans le chemin direct illustré, elles sont traitées séquentiellement par le consommateur propriétaire de cette partition. Sans clé, elles peuvent se retrouver dans plusieurs partitions et s'exécuter en parallèle. Ne pas transformer cette garantie locale en ordre global. Le guide avertit également qu'un changement du nombre de partitions remappe les clés et exige une planification. La question du choix de clé précède donc celle du nombre de pods.

### Sources

- doc/fr/architecture_et_conception_des_topics.md, lignes 50-91 et 165-180
- doc/fr/rapports.md, lignes 14-26

## 10 - Ni topic universel, ni multiplication sans intention

Le guide propose de chercher un équilibre autour des agrégats, des responsabilités et des contrats.

### Notes

Présenter les deux extrêmes puis le compromis du guide. Un Fat pipe mélange des événements et complique responsabilité, filtrage, schémas et choix de clé. Des Thin pipes spécialisés clarifient les contrats, mais fragmentent les séquences entre topics et augmentent la surface opérationnelle. L'orientation proposée consiste à regrouper les événements d'une même entité ou d'un agrégat quand leur ordre est essentiel, tout en isolant les données lorsque leurs exigences l'imposent. Les topics du sandbox sont focalisés sur le traitement des requêtes et leur DLT; le projet ne modélise pas tout un catalogue d'entreprise.

### Sources

- doc/fr/architecture_et_conception_des_topics.md, lignes 120-163 et 180-182

## 11 - Une seule option change; l'invariant peut basculer

Les deux rapports du guide utilisent le même stock initial et les mêmes requêtes, avec ou sans clé.

### Notes

Cette diapositive présente les rapports inclus dans l'archive. Elle ne constitue pas une nouvelle exécution ni une prédiction du chiffre obtenu à chaque essai. Avec la clé, le rapport documente des quantités non négatives et des demandes BACK_ORDER lorsque le stock est insuffisant. Sans clé, Banana est à -10. Faire verbaliser le lien entre partitionnement, concurrence et stock avant de passer au mécanisme de course. Question : un tableau de bord indiquant que les messages ont été consommés suffirait-il à détecter cette anomalie métier ? Réponse attendue : non, il faut observer l'état et ses invariants.

### Sources

- doc/fr/rapports.md, lignes 5-26 et 35-59
- doc/asserts/Withkey-Report.png (extrait recadré)
- doc/asserts/Nokey-Report.png (extrait recadré)

## 12 - Protéger la personne ne protège pas le produit

Le verrou Hazelcast est indexé sur PERSON_ID; la ligne INVENTORY est partagée par PRODUCT_ID.

### Notes

Décrire la course check-then-act telle qu'elle est documentée : deux requêtes visant le même produit lisent un stock encore suffisant, passent la vérification, puis appliquent chacune leur décrément. Le verrou par personne ne sérialise pas deux clients différents qui visent ce produit. Le sandbox laisse cette course visible; Inventory n'a ni @Version ni verrou pessimiste dans le scénario décrit. Le partitionnement par produit ferme cette course dans le chemin direct documenté. Le guide expose aussi des protections SQL et des verrous comme approfondissements. Ne pas effacer l'anomalie avant de l'avoir expliquée aux étudiants.

### Sources

- doc/fr/base_de_donnees.md, lignes 315-345
- doc/fr/patrons_persistance_et_transaction.md, lignes 437-475

## 13 - Trois garanties, mais toujours une frontière à préciser

La question n'est pas seulement combien de fois un message arrive, mais quels effets deviennent visibles.

### Notes

Le guide distingue les trois sémantiques. At-most-once accepte une perte possible lorsque la position est validée avant le travail. At-least-once accepte la redélivrance lorsque le travail est validé avant l'offset. L'EOS Kafka rend atomiques les résultats Kafka et les offsets sources dans une boucle lecture-traitement-écriture, mais n'englobe pas automatiquement la base SQL ou un appel distant. Insister sur le choix du projet : at-least-once plus traitement idempotent. La durabilité de la publication dépend aussi de la configuration du producteur et de la réplication; aucune formulation ne doit devenir une promesse absolue d'absence de perte.

### Sources

- doc/fr/semantiques_livraison_et_fiabilite.md, lignes 21-53
- doc/fr/reconnaissance_et_idempotence_consommateur.md, lignes 152-163

## 14 - Trois mécanismes différents travaillent ensemble

Idempotence du producteur, transaction Kafka et isolation du consommateur ne répondent pas à la même question.

### Notes

Rappeler le sujet read_committed en le situant dans le raisonnement. L'idempotence du producteur protège contre les doublons de ses nouvelles tentatives, pas contre deux publications applicatives du même fait. La transaction Kafka définit un ensemble de publications validé ou annulé. read_committed empêche le consommateur de restituer les messages de transactions annulées et attend leur issue. Le guide documente cette combinaison pour le chemin direct. Il ne faut pas en déduire un commit SQL ni présumer que tous les clients Kafka construits séparément héritent de la même configuration. Chaque source/consommateur doit être examiné dans son propre périmètre.

### Sources

- doc/fr/semantiques_livraison_et_fiabilite.md, lignes 84-105 et 107-163
- doc/fr/architecture_et_conception_des_topics.md, lignes 169-180

## 15 - Le commit métier doit précéder l'acquittement

Une panne entre les deux validations provoque une relivraison : c'est précisément le rôle de l'idempotence.

### Notes

Expliquer la frontière du projet, sans confondre flush, retour de méthode et commit. ProcessorSrv.process est appelé via son proxy transactionnel; le listener acquitte après le retour de ce traitement. Si le commit SQL a réussi mais que le processus s'arrête avant l'acquittement, le message peut revenir. Un acquittement placé avant un commit SQL finalement annulé pourrait au contraire perdre l'effet attendu. Le callback afterCommit rend l'ordre explicite, mais ne supprime pas la fenêtre de panne. Question aux étudiants : faut-il empêcher toute relivraison, ou rendre ses effets sans danger ? Réponse : concevoir le traitement idempotent.

### Sources

- doc/fr/reconnaissance_et_idempotence_consommateur.md, lignes 32-65, 90-119 et 212-216
- doc/fr/patrons_persistance_et_transaction.md, lignes 225-254

## 16 - Reconnaître l'événement dans la même transaction

Un même événement livré plusieurs fois ne doit appliquer l'effet métier qu'une seule fois.

### Notes

Montrer les deux niveaux décrits dans le chapitre consommateur. Le résultat métier indique si la requête est encore PENDING ou BACK_ORDER, ou déjà réglée. Lorsqu'un en-tête est présent, le journal est recherché sur la clé composite CONSUMER_ID, EVENT_ID. L'insertion ou la mise à jour de ce marqueur et les effets métier appartiennent à la même transaction SQL. Une contrainte d'unicité reste l'autorité de déduplication face à la concurrence. Un flush précoce peut déclencher la vérification avant le travail redondant; il ne constitue pas le commit. Garder distincts l'identité, le type de traitement et son résultat.

### Sources

- doc/fr/reconnaissance_et_idempotence_consommateur.md, lignes 165-216
- doc/fr/base_de_donnees.md, section JRN_EVENT et lignes 230-259
- doc/fr/patrons_persistance_et_transaction.md, lignes 225-254

## 17 - Une erreur doit suivre un parcours explicite

Réessayer un incident transitoire, isoler un échec persistant, puis organiser un rejeu contrôlé.

### Notes

DLT signifie Dead Letter Topic : un topic dédié aux échecs qui demandent une analyse. Distinguer les mots. Une nouvelle tentative traite à nouveau un échec, selon une politique bornée. Une relivraison peut survenir après une panne et une reprise d'offset. Un rejeu est une décision de retraitement et doit respecter les règles métier et l'idempotence. Les chemins Kafka et Flink possèdent des DLT associées dans le guide. L'échec durable doit conserver suffisamment de contexte pour l'analyse. Ne pas transformer DLT en oubli des erreurs : la gouvernance et l'exploitation doivent définir qui analyse, corrige et relance. Ne pas donner ici une garantie universelle sur les exceptions non réessayables, dont la classification doit être vérifiée dans la version utilisée.

### Sources

- doc/fr/semantiques_livraison_et_fiabilite.md, lignes 177-203
- doc/fr/guides_flink.md, lignes 175-191
- doc/fr/gouvernance_et_observabilite.md, lignes 405-409
- doc/fr/reconnaissance_et_idempotence_consommateur.md, lignes 215-218

## 18 - Deux commits indépendants laissent une fenêtre de divergence

Mettre à jour une base SQL puis publier dans Kafka ne forme pas automatiquement une seule transaction.

### Notes

Introduire le problème général de double écriture du guide, pas une nouvelle description certifiée de l'implémentation. Si la base est validée et que la publication n'arrive jamais, les consommateurs ne sont pas informés. Si l'événement devient visible et que la base est annulée, l'aval agit sur un changement absent. Le chapitre explique pourquoi un enchaînement au meilleur effort de gestionnaires de transactions ne supprime pas cette fenêtre. L'Outbox sera présentée comme un patron du guide et une extension du sandbox, pas comme un composant déjà activé par défaut.

### Sources

- doc/fr/patrons_persistance_et_transaction.md, lignes 25-71 et 407-409
- doc/fr/reconnaissance_et_idempotence_consommateur.md, lignes 121-150

## 19 - L'Outbox ramène l'atomicité dans une seule ressource

La donnée métier et l'événement à publier sont enregistrés ensemble; un relais assure ensuite la publication.

### Notes

Présenter l'Outbox comme un patron documenté mais non implémenté par défaut dans le sandbox. La transaction SQL écrit les données et la ligne OUTBOX. Après le commit, un poller ou un connecteur CDC publie les événements. Le CDC lit les changements validés du journal de transactions et peut aussi relayer une Outbox. Les deux notions ne sont donc pas toujours concurrentes. Un relais peut republier après une panne; l'idempotence du consommateur reste nécessaire. La politique de rétention de l'Outbox et son exploitation font partie du patron, même si elles ne sont pas détaillées sur la diapositive.

### Sources

- doc/fr/patrons_persistance_et_transaction.md, lignes 69-178 et 407-409, 481-506

## 20 - Flink change le transport, pas l'intention métier

Le toggle Flink choisit le topic de publication; les deux chemins convergent vers le même traitement.

### Notes

Revenir au schéma de la requête maintenant que les garanties du chemin direct sont comprises. Le mode direct utilise APP-Kafka-Requests puis KafkaPipelineListener. Le mode Flink utilise APP-Flink-requests, un job Flink et ProcessorSink. Les deux délèguent ensuite au cœur PipelineSrv.handle et au processeur transactionnel. La documentation décrit le job du sandbox comme une source et un sink, sans transformation intermédiaire. Cette simplicité permet de comparer le modèle de progression et de reprise. Ne pas présenter le MiniCluster comme un cluster distribué; la limite in-JVM sera étudiée après les checkpoints.

### Sources

- doc/fr/guides_flink.md, lignes 24-35 et 66-74

## 21 - Un checkpoint Flink n'est pas un commit SQL

Le dernier checkpoint terminé fixe le point de reprise Flink, pas l'état déjà validé dans SQL.

### Notes

Un checkpoint conserve un point de reprise du job, notamment les offsets de la source dans son état. Le guide documente un checkpoint périodique, configuré à 5 secondes dans l'exemple, mais le point essentiel est la frontière de reprise : le dernier checkpoint terminé. Les commits SQL du sink ont lieu par enregistrement, indépendamment de cette frontière. Après une panne, certains effets SQL déjà validés peuvent donc être sollicités de nouveau. L'idempotence protège l'état métier; elle ne rend pas automatiquement idempotents tous les compteurs. Le guide signale explicitement cette limite pour les statistiques de complétion. Ne pas présenter l'intervalle configuré comme une borne absolue de reprise si des checkpoints ne se terminent pas.

### Sources

- doc/fr/guides_flink.md, lignes 119-172 et 280-282
- doc/fr/flink_springboot.md, lignes 202-255

## 22 - Dans le chemin Flink, la pause passe par un savepoint

Le sandbox arrête le job avec un point de sauvegarde, puis le reconstruit depuis ce point à la reprise.

### Notes

Comparer sans assimiler. La pause du listener Spring Kafka est une opération du conteneur de consommation. Dans le chemin Flink du guide, l'opération utilisée est stop-with-savepoint : le job s'arrête, un chemin de sauvegarde est conservé, puis la reprise reconstruit le graphe avec ce chemin. Cela prend du temps et bloque la demande pendant la sauvegarde. Le stockage est temporaire local par défaut et le chemin est conservé en mémoire; un usage réel demande une durabilité et une procédure explicites. Le savepoint ne remplace pas l'idempotence des écritures SQL externes.

### Sources

- doc/fr/guides_flink.md, lignes 195-238 et 269-270

## 23 - Le pont in-JVM n'est pas une intégration distribuée

Un TaskManager distant ne partage ni les beans Spring, ni les repositories JPA, ni l'état statique de l'application.

### Notes

La documentation présente le pont entre le sink et le service Spring comme un raccourci pédagogique valable dans la JVM du MiniCluster. Il ne peut pas être transporté tel quel vers des TaskManagers distants. Le guide d'intégration recommande des runtimes séparés et un module de logique métier Java indépendant du framework. Les options documentées comprennent la sortie vers Kafka puis un consommateur Spring, un sink natif pris en charge ou un appel de service. Autre limite documentée du chemin value-only : la clé et les en-têtes Kafka ne traversent pas tels quels le saut Flink. Ces différences doivent rester visibles dans la comparaison.

### Sources

- doc/fr/guides_flink.md, lignes 66-116 et 228-239
- doc/fr/flink_springboot.md, lignes 24-36, 65-98 et 330-341

## 24 - Le nombre de partitions fixe le plafond utile

Ajouter des pods n'ajoute du parallélisme que s'il reste des partitions à leur attribuer.

### Notes

Relier trois niveaux que les étudiants confondent facilement : le pod, le thread consommateur et la partition. Le parallélisme utile est le minimum entre partitions et pods multipliés par threads par pod. Le tableau est un exemple de dimensionnement à huit partitions, cohérent avec les exemples du guide; il ne certifie pas les valeurs effectives de configuration de toutes les versions du projet. Demander combien de threads sont utiles avec cinq pods et deux threads chacun : huit sur dix au maximum. Les chapitres de l'archive ne donnent pas tous la même valeur de concurrence par défaut; il faut donc vérifier la configuration utilisée avant le laboratoire.

### Sources

- doc/fr/architecture_et_conception_des_topics.md, lignes 93-118
- doc/fr/distribution_scale_et_arret.md, lignes 25-34 et 171-185
- doc/fr/elasticite_horizontale_eks.md, lignes 106-127

## 25 - Changer les consommateurs change la propriété des partitions

Scaling, redémarrage et défaillance font partie du fonctionnement normal d'un groupe.

### Notes

Le rééquilibrage redistribue les partitions quand la composition du groupe change. Il peut créer une interruption et une relivraison d'enregistrements non acquittés; son impact dépend du protocole et de la stratégie. Le guide distingue la détection d'une absence de heartbeats et celle d'un traitement qui n'appelle plus poll dans le délai prévu. Le réglage des lots et des timeouts doit refléter la durée du travail. Les mécanismes coopératifs, l'identité stable et la stabilisation du scaling réduisent certains mouvements, sans supprimer le besoin d'idempotence. Ne pas annoncer une durée universelle de rééquilibrage.

### Sources

- doc/fr/cycle_vie_et_operations.md, lignes 187-251
- doc/fr/distribution_scale_et_arret.md, lignes 118-169

## 26 - Mettre en pause n'est pas arrêter le consommateur

Pour suspendre temporairement le travail sans quitter le groupe, le guide utilise pause() / resume().

### Notes

Présenter le tableau comme une distinction du conteneur Spring Kafka. pause suspend la demande de nouveaux enregistrements tout en maintenant la vie du consommateur dans le groupe. stop arrête le conteneur et provoque un changement de composition du groupe. Cela ne signifie pas que pause peut garantir l'absence de tout rééquilibrage causé ailleurs dans le cluster; cela signifie qu'elle n'exige pas en elle-même une sortie du groupe. Dans le sandbox, les événements de pause découplent le cœur du pipeline des composants de transport. Faire comparer avec la pause Flink par savepoint pour consolider les deux modèles mentaux.

### Sources

- doc/fr/cycle_vie_et_operations.md, lignes 270-296
- doc/fr/guides_flink.md, lignes 195-200

## 27 - L'arrêt fait partie de la correction du système

Le temps de grâce doit permettre de terminer le travail en cours et de fermer les ressources dans le bon ordre.

### Notes

Suivre la séquence opérationnelle sans promettre qu'elle couvre une panne de machine. La plateforme retire le pod du trafic, exécute éventuellement preStop, transmet SIGTERM, puis impose SIGKILL si le délai expire. L'application doit cesser d'accepter du nouveau travail, laisser terminer l'en-cours, valider les effets puis les offsets correspondants et fermer les producteurs/consommateurs. Le délai global comprend le preStop et les phases d'arrêt applicatives. Un arrêt brutal peut empêcher tout nettoyage; la sémantique de reprise et l'idempotence restent donc nécessaires. Cette diapositive ne reprend pas les valeurs de timeout du guide comme des règles universelles.

### Sources

- doc/fr/cycle_vie_et_operations.md, lignes 85-153
- doc/fr/distribution_scale_et_arret.md, lignes 260-301 et 332-346

## 28 - Trois sondes répondent à trois questions différentes

Une panne d'accès au service et un processus irrécupérable ne demandent pas la même réaction.

### Notes

Le guide distingue startup, liveness et readiness. Startup protège la phase de démarrage. Liveness porte sur la santé du processus et peut conduire à son redémarrage. Readiness porte sur sa capacité à recevoir le trafic du service et retire le pod des endpoints sans l'arrêter. Les indicateurs de santé doivent être rapides; les durées, compteurs et jauges relèvent des métriques. Le projet fournit des commandes pour basculer liveness/readiness et observer Actuator. Souligner que la readiness HTTP ne remplace pas le pilotage explicite de la consommation Kafka.

### Sources

- doc/fr/cycle_vie_et_operations.md, lignes 331-369
- doc/fr/gouvernance_et_observabilite.md, lignes 286-320, 498-506 et 513-526

## 29 - KEDA et Karpenter agissent à deux échelles différentes

Le retard Kafka justifie des consommateurs; les pods non planifiables justifient de la capacité de nœuds.

### Notes

Le chapitre d'élasticité distingue le besoin applicatif et le besoin d'infrastructure. KEDA utilise le lag Kafka pour piloter les répliques de consommateurs. Karpenter répond aux pods non planifiables en fournissant de la capacité de nœuds. Le CPU seul peut manquer un retard causé par l'attente de la base, d'un verrou ou d'un appel réseau. Le nombre de répliques utiles doit toutefois tenir compte des threads par pod et des partitions. Introduire cette architecture comme un patron de déploiement du guide, pas comme une fonctionnalité opérationnelle déjà démontrée dans le sandbox embarqué.

### Sources

- doc/fr/elasticite_horizontale_eks.md, lignes 19-39, 72-127 et 135-205

## 30 - Une clé déséquilibrée peut neutraliser le scaling

Le débit peut rester limité par une seule partition, même lorsque les autres consommateurs sont disponibles.

### Notes

Le schéma illustre un déséquilibre; il ne représente pas une mesure du sandbox. Le guide explique qu'une clé très dominante concentre le trafic sur une partition. Augmenter le nombre de consommateurs ne divise pas automatiquement le travail de cette partition. Avant de changer le partitionnement, revenir à l'exigence d'ordre par entité. La distribution, les verrous, la base SQL et les appels externes doivent être considérés ensemble. Le guide propose plusieurs mitigations, mais le support n'en fait pas une recette universelle : le compromis entre ordre et débit doit être explicité.

### Sources

- doc/fr/architecture_et_conception_des_topics.md, lignes 60-91 et 218-225
- doc/fr/distribution_scale_et_arret.md, lignes 189-219
- doc/fr/elasticite_horizontale_eks.md, lignes 19-31

## 31 - Rendre visibles le flux, l'attente et les échecs

Les tableaux de bord du sandbox complètent Actuator; les métriques et les corrélations permettent l'enquête.

### Notes

La capture fournie montre le tableau Kafka au repos, pas une mesure d'un incident. Elle permet de repérer les topics, partitions, répliques et groupes. Le guide complète cette vue avec le lag, les durées, les erreurs, les retries/DLT et les identifiants de corrélation. L'observabilité aide à répondre à trois questions : est-ce que le système fonctionne, où attend-il et quel événement a produit quel effet ? Le port de management et les détails de santé doivent rester exposés de façon contrôlée. Ne pas annoncer ici qu'un scrape ou une alerte ont été validés par cette présentation.

### Sources

- doc/fr/gouvernance_et_observabilite.md, lignes 320-374, 417-478 et 496-526
- doc/asserts/kafka_dashboard.png (capture fournie, recadrée)

## 32 - Un contrat transforme un topic en interface compréhensible

La gouvernance rend explicites le sens des données, les responsabilités et les conditions de leur usage.

### Notes

Présenter cette fiche comme un modèle de gouvernance issu du guide, pas comme l'inventaire d'un catalogue déjà déployé. Documenter la signification, le propriétaire, le schéma, les producteurs/consommateurs, les objectifs de service et les règles d'accès/rétention. La compatibilité du schéma et le cycle de vie du contrat doivent être explicites. Le guide Data Mesh/Data Fabric rappelle que les types Java du sandbox forment un contrat implicite; une plateforme d'entreprise doit externaliser la découverte, les métadonnées et la gouvernance. Question : qui est responsable si un message reste en DLT ? Ce responsable appartient au contrat opérationnel.

### Sources

- doc/fr/gouvernance_et_observabilite.md, lignes 89-156
- doc/fr/datamesh_datafabric.md, lignes 62-75 et 172-190

## 33 - La propriété et la plateforme sont complémentaires

Le guide ne présente pas Data Mesh et Data Fabric comme deux produits concurrents.

### Notes

Le Data Mesh porte sur le modèle d'exploitation : propriété par domaine, données comme produit, plateforme libre-service et gouvernance fédérée. Le Data Fabric porte sur les capacités partagées qui relient des données distribuées : intégration, métadonnées, qualité, accès et observabilité. Le guide explique qu'un modèle hybride peut combiner les deux et que Kafka n'est pas obligatoire pour tout produit de données. Un topic ne devient pas automatiquement un produit et un cluster Kafka ne constitue pas à lui seul un Fabric. Le sandbox reste une application pédagogique; il ne constitue ni l'un ni l'autre à l'échelle d'une entreprise.

### Sources

- doc/fr/datamesh_datafabric.md, lignes 20-44, 81-94, 127-159 et 172-179

## 34 - Un message consommé ne prouve pas que l'état est juste

La corroboration compare des représentations des mêmes faits et recherche les divergences.

### Notes

Revenir à Banana : un pipeline peut consommer les messages tout en validant une quantité incorrecte. La corroboration complète l'idempotence et les garanties de livraison. Dans ce projet centré sur la base, les tables relationnelles constituent l'état de référence; Kafka déclenche leurs mutations. Il ne faut donc pas présenter le sandbox comme une implémentation d'event sourcing. Comparer les demandes exécutées, leurs quantités et opérations, et les mouvements d'inventaire attendus. Les comptages par résultat constituent un premier signal; ils ne suffisent pas à prouver chaque invariant.

### Sources

- doc/fr/verification_donnees.md, lignes 24-34, 71-75 et 179-211
- doc/fr/rapports.md, lignes 49-52

## 35 - Des contrôles en couches, puis une réparation traçable

Utiliser des signaux fréquents et peu coûteux, puis approfondir l'enquête là où un écart apparaît.

### Notes

Le guide propose une stratégie en couches, pas un seul contrôle miracle. Les comptages et sommes de contrôle signalent rapidement un écart. Les snapshots et invariants donnent des repères périodiques. Le rejeu et les consommateurs fantômes permettent une comparaison approfondie, notamment pour une migration. Les hachages exigent un état représenté de façon déterministe. Une divergence doit être localisée, classée, réparée puis consignée. Ne pas assimiler tout PENDING transitoire à un incident : le travail en cours est normal. Distinguer ce qui est déjà visible via countRequest du dispositif complet de réconciliation qui est un patron du guide.

### Sources

- doc/fr/verification_donnees.md, lignes 59-148 et 192-222

## 36 - Le sandbox rend les limites visibles; il ne les efface pas

La facilité d'exécution locale et les garanties d'une architecture de production sont deux objectifs distincts.

### Notes

Cette diapositive synthétise les limites explicitement assumées par la documentation. Les moteurs et la base sont embarqués pour apprendre. Les valeurs de configuration et les raccourcis in-JVM ne sont pas des contrats de production. L'Outbox, le catalogue, le partage de l'état, la sécurisation et l'autoscaling nécessitent une conception et une validation propres à la cible. Ne pas transformer cette liste en rapport d'audit : les constats et corrections de la revue de code précédente ne sont pas le sujet de ce support. L'objectif est que les étudiants sachent distinguer une expérience reproductible d'une architecture d'exploitation complète.

### Sources

- doc/fr/LISEZ_MOI.md, lignes 19-25
- doc/fr/guides_sandbox.md, lignes 17-35 et 295-303
- doc/fr/guides_flink.md, lignes 101-116 et 219-225
- doc/fr/datamesh_datafabric.md, lignes 172-190

## 37 - Ce ne sont pas trois marches d'une échelle de maturité

Le guide compare des styles d'intégration complémentaires qui placent la complexité à des endroits différents.

### Notes

Conserver le cadrage du chapitre de conclusion : ETL signifie ici surtout intégration par lots et SOA surtout interactions synchrones par services/API. Ces définitions sont le périmètre de la comparaison, pas des définitions exhaustives. ETL organise les calendriers, transformations et contrôles de qualité. SOA organise les interfaces, appels et dépendances de disponibilité. EDA organise les événements, contrats, reprises et effets asynchrones. L'EDA réduit certains couplages, mais maintient le couplage sémantique et de plateforme. Le guide recommande de choisir un mécanisme suffisant pour chaque interaction et envisage un modèle hybride.

### Sources

- doc/fr/conclusion_etl_soa_eda_tco_roi.md, lignes 20-71 et 173-177

## 38 - Justifier l'EDA par sa valeur, pas par le choix du broker

Comparer les options sur le même horizon et expliciter les hypothèses, les coûts et les bénéfices.

### Notes

Le chapitre TCO/ROI ne fournit pas un business case chiffré pour ce projet; il fournit un cadre de décision. Les coûts comprennent la plateforme, l'ingénierie, l'exploitation, l'exactitude et la gouvernance, pas seulement Kafka. Les bénéfices peuvent venir de la réutilisation des événements, de la réactivité, du découplage et du rejeu. Ils doivent être reliés à des résultats mesurables et comparés à une option plus simple. L'ADR consigne le besoin, les garanties, les hypothèses, les responsabilités et les critères de réévaluation. Ne pas inventer de pourcentage de ROI ni de gains financiers.

### Sources

- doc/fr/conclusion_etl_soa_eda_tco_roi.md, lignes 90-143 et 158-171

## 39 - Quatre expériences pour relier les concepts aux observations

Préparer un état de départ comparable, ne changer qu'une variable et expliquer ce qui est observé.

### Notes

Proposition pédagogique construite à partir des expériences du guide, non procédure de test déjà exécutée. 1. Comparer avec et sans clé sur le même jeu de données : observer l'ordre et les quantités, puis expliquer la course. 2. Tester un rejeu autorisé et observer les statuts et le journal : identifier ce qui doit rester sans effet en double. 3. Comparer la pause directe et la pause Flink : distinguer appartenance au groupe et savepoint. 4. Examiner les demandes traitées et le stock : montrer pourquoi le transport seul ne prouve pas l'exactitude. Préparer la version corrigée et les scénarios avant la séance; ne pas déclencher de remise à zéro pendant un lot actif.

### Sources

- doc/fr/rapports.md, lignes 5-59
- doc/fr/reconnaissance_et_idempotence_consommateur.md, lignes 208-218
- doc/fr/guides_flink.md, lignes 195-238
- doc/fr/verification_donnees.md, lignes 179-222

## 40 - Une EDA résiliente se construit de bout en bout

La valeur du projet est de rendre visibles les responsabilités que le transport seul ne prend pas en charge.

### Notes

Reprendre les six acquis sans ajouter de nouvelle garantie. La clé exprime l'ordre métier. La frontière transactionnelle définit ce qui est atomique. L'idempotence permet de tolérer la relivraison. Le runtime et les partitions bornent le parallélisme et la reprise. L'observabilité et la corroboration distinguent fonctionnement et exactitude. Enfin, gouvernance et TCO/ROI ramènent le choix technologique au besoin. Question de clôture : pour une nouvelle intégration, quelles preuves demanderiez-vous avant de qualifier son traitement de fiable ? Les annexes donnent les repères pour poursuivre la lecture et préparer les discussions techniques.

### Sources

- doc/fr/architecture_et_conception_des_topics.md, lignes 208-225
- doc/fr/reconnaissance_et_idempotence_consommateur.md, lignes 238-246
- doc/fr/distribution_scale_et_arret.md, lignes 25-34 et 260-301
- doc/fr/verification_donnees.md, lignes 213-222
- doc/fr/conclusion_etl_soa_eda_tco_roi.md, lignes 145-177

## 41 - Lire JRN_EVENT sans confondre ses responsabilités

Le journal distingue l'identité d'un traitement, sa corrélation, sa nature et son issue.

### Notes

Cette annexe s'appuie sur le schéma JRN_EVENT, pas sur une nouvelle interprétation des en-têtes de rejeu. CONSUMER_ID et EVENT_ID constituent la clé composite. BATCH_ID corrèle une publication. EVENT_TYPE indique une première livraison ou un rejeu. RESULT indique l'issue. PAYLOAD_HASH et les dates enrichissent la traçabilité. Dans la documentation, PROCESSED_AT n'est renseigné que lorsque la requête a été exécutée. Le chapitre de reconnaissance contient aussi une formulation sur replay-id qui n'est pas alignée avec la clé composite présentée ici; cette divergence documentaire est à clarifier avant d'en faire une règle de déduplication pour le cours.

### Sources

- doc/fr/base_de_donnees.md, section JRN_EVENT et lignes 230-259
- doc/fr/reconnaissance_et_idempotence_consommateur.md, lignes 216-217

## 42 - Choisir le runtime avant de produire l'artefact

Un job DataStream autogéré, un client Table API et une fonction gérée n'ont pas le même contrat de déploiement.

### Notes

Le guide d'intégration décrit plusieurs modèles. Dans le runtime local, Spring peut assembler la configuration et suivre le JobClient. Dans un cluster autogéré, il est préférable de garder des opérateurs indépendants avec des connecteurs pris en charge. Un contexte Spring minimal dans un opérateur est une option avancée, à initialiser sur le worker et à dimensionner par sous-tâche; il multiplie potentiellement les pools de connexions. Le chapitre distingue aussi SQL, Table API et artefacts de fonctions sur une plateforme gérée. Ces lignes résument les modèles du document fourni : elles ne vérifient pas le catalogue actuel des interfaces disponibles d'un fournisseur.

### Sources

- doc/fr/flink_springboot.md, lignes 52-63, 151-200, 265-271 et 305-341

## 43 - Retrouver les chapitres du parcours dans le ZIP

Les chemins sont relatifs à doc/fr/. Chaque diapositive possède aussi ses sources détaillées dans les notes.

### Notes

Cette carte de lecture permet de revenir à la documentation originale après la présentation. Les chapitres sources gardent un niveau de détail plus élevé : code, configurations, contraintes et variantes. Le support n'a pas vocation à les remplacer. Les sources principales sont les fichiers français du guide et les captures qu'ils référencent. Les rapports illustrent des exécutions déjà incluses dans le dépôt. Les présentations PowerPoint historiques dans doc/asserts ne sont pas traitées comme une nouvelle source normative; le modèle visuel utilisé est celui fourni avec la demande.

### Sources

- doc/fr/LISEZ_MOI.md, lignes 51-70
- doc/fr/architecture_et_conception_des_topics.md
- doc/fr/base_de_donnees.md
- doc/fr/rapports.md
- doc/fr/semantiques_livraison_et_fiabilite.md
- doc/fr/reconnaissance_et_idempotence_consommateur.md
- doc/fr/patrons_persistance_et_transaction.md
- doc/fr/guides_sandbox.md

## 44 - Poursuivre de la résilience technique à la confiance

La progression relie les mécanismes d'exécution, l'exploitation, la gouvernance et le choix d'architecture.

### Notes

Compléter la carte de lecture. Le guide Flink décrit le chemin concret du sandbox; le guide d'intégration Spring Boot/Flink expose les options et limites pour d'autres runtimes. Les chapitres d'opérations et d'élasticité approfondissent l'arrêt et la distribution. La gouvernance, le Data Mesh/Data Fabric et la corroboration portent la confiance au-delà de la seule livraison de messages. Le chapitre de conclusion relie ces exigences au choix ETL/SOA/EDA et à son coût complet. La progression et les propositions d'atelier sont une organisation pédagogique de ces sources, pas un ajout de résultats expérimentaux.

### Sources

- doc/fr/guides_flink.md
- doc/fr/flink_springboot.md
- doc/fr/cycle_vie_et_operations.md
- doc/fr/distribution_scale_et_arret.md
- doc/fr/elasticite_horizontale_eks.md
- doc/fr/gouvernance_et_observabilite.md
- doc/fr/verification_donnees.md
- doc/fr/datamesh_datafabric.md
- doc/fr/conclusion_etl_soa_eda_tco_roi.md

