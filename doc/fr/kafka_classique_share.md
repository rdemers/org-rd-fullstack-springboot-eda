
# Approche classique vs. Share Groups

> Fait partie du **Kafka Engineering Guide** de `org-rd-fullstack-springboot-eda`. Voir le [LISEZ_MOI du projet](./LISEZ_MOI.md).

**Portée :** Apache Kafka propose deux modèles de consommation qui répondent à des besoins différents : le **Consumer Group classique** et le **Share Group**, introduit avec le protocole de consommation partagée (KIP-932).

## Table des matières

- [Consommateur](#consommateur)
- [Approche classique : Consumer Group](#approche-classique--consumer-group)
- [Nouvelle approche : Share Group (KIP-932)](#nouvelle-approche--share-group-kip-932)
- [Tableau comparatif](#tableau-comparatif)
- [Impacts sur l&#39;architecture EDA](#impacts-sur-larchitecture-eda)
- [Synthèse architecturale](#synthèse-architecturale)

## **Consommateur**

La différence fondamentale concerne la manière dont Kafka distribue les messages et gère leur traitement.

- **Consumer Group classique :** les partitions sont attribuées aux consommateurs. Chaque partition est consommée par un seul consommateur du groupe à la fois.
- **Share Group :** les messages peuvent être distribués entre plusieurs consommateurs du même groupe, même lorsqu'ils appartiennent à une seule partition.

## Approche classique : Consumer Group

Une partition est attribuée à un seul consommateur à la fois au sein du groupe. Le Consumer Group classique privilégie la lecture séquentielle des partitions, avec une gestion de la progression basée sur les offsets.

**Caractéristiques :**

- Un seul consommateur actif par partition et par groupe.
- Le parallélisme est limité par le nombre de partitions.
- L'ordre des messages est préservé au niveau de la lecture d'une partition.
- La progression est suivie à l'aide des offsets validés.
- Un rééquilibrage peut être nécessaire lorsque la répartition des consommateurs change.

Ce modèle convient particulièrement aux traitements qui exigent un ordre de lecture prévisible, aux projections d'états métier et aux applications de traitement de flux.

## Nouvelle approche : Share Group (KIP-932)

Plusieurs consommateurs peuvent traiter des messages provenant de la même partition. Le Share Group privilégie la distribution du travail et le parallélisme, avec un suivi individuel des messages.

**Caractéristiques :**

- Plusieurs consommateurs peuvent partager une même partition.
- Le parallélisme n'est plus limité par le nombre de partitions.
- L'ordre global de traitement des messages n'est plus garanti, même à l'intérieur d'une partition.
- Chaque message possède son propre état de traitement.
- Les échecs peuvent entraîner une redistribution individuelle des messages.
- Kafka comptabilise les tentatives de livraison afin de limiter les reprises infinies.

Cette approche rapproche Kafka d'un modèle de file de travaux, sans transformer ses topics en files de messages traditionnelles.

## Tableau comparatif

| Critère                   | Consumer Group                            | Share Group                                  |
| -------------------------- | ----------------------------------------- | -------------------------------------------- |
| Unité de distribution     | Partition                                 | Messages / lots                              |
| Affectation des partitions | Exclusive                                 | Partagée                                    |
| Parallélisme              | Limité par les partitions                | Indépendant du nombre de partitions         |
| Ordre de lecture           | Garanti par partition                     | Non garanti entre les lots                   |
| Suivi de la progression    | Validation des offsets                    | Acquittement individuel                      |
| Gestion des échecs        | Relecture depuis les offsets non validés | Relivraison des messages non acquittés      |
| Messages problématiques   | Gestion au niveau de l'application        | Compteur de tentatives et archivage          |
| Cas d'utilisation          | Flux ordonnés, états métier            | Travaux indépendants, traitement parallèle |

## Impacts sur l'architecture EDA

Le choix entre les deux modèles devrait principalement dépendre de la sémantique métier des événements.

**Consumer Group classique :** à privilégier lorsque les événements représentent une succession de changements d'état dont l'ordre est important.

Exemple : création d'une commande, paiement, expédition et annulation.

**Share Group :** à privilégier lorsque les messages représentent des travaux indépendants pouvant être exécutés dans n'importe quel ordre.

Exemple : génération de documents, envoi de notifications ou traitement parallèle de fichiers.

Un point important : **le Share Group ne supprime pas le besoin d'idempotence**. La livraison reste de type *at-least-once*. Une panne après le commit métier, mais avant l'acquittement Kafka, peut entraîner une nouvelle livraison du message.

## Synthèse architecturale

> Le **Consumer Group classique** privilégie la cohérence et l'ordre des flux d'événements, avec un parallélisme déterminé par les partitions.
>
> Le **Share Group** privilégie la distribution dynamique des traitements, avec un parallélisme indépendant des partitions, au prix d'une perte de garantie sur l'ordre de traitement.
>
> Les deux modèles sont complémentaires : le premier convient davantage au traitement des événements métier ordonnés, tandis que le second répond aux besoins de traitement concurrent de travaux indépendants.

**Point de vigilance :** les Share Groups sont apparus en accès anticipé dans Kafka 4.0, puis en préversion dans Kafka 4.1. Avant une adoption en production, il faut vérifier la version du cluster, la disponibilité effective de la fonctionnalité et sa prise en charge par la plateforme Kafka utilisée.
