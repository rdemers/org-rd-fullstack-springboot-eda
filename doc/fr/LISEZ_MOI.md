# org-rd-fullstack-springboot-eda

[&#127760; English version](../../README.md)

## Application fullstack avec un sandbox Kafka/Flink/Hazelcast construit sur Spring Boot et Nuxt

Ce projet offre un environnement sandbox complet pour explorer les principes, modèles et concepts de la construction de systèmes logiciels événementiels résilients. Il souligne les contraintes, compromis et défis liés à la conception et à la mise en œuvre de composants d'architecture événementielle (EDA).

La plateforme utilise Spring Boot, Nuxt, Apache Maven, Kafka, Flink, Hazelcast et Docker pour créer un conteneur d'application conforme OCI. Elle comprend une collection de microservices conçus pour le déploiement sur AWS/EKS et une application web basée sur les principes SSG (Static Site Generation).

**Remarque :** Dans cette architecture, Spring Boot regroupe les services backend et le frontend Nuxt dans un seul artefact déployable. Il expose les services de l'application et sert les ressources statiques du frontend, agissant comme une couche légère de distribution de contenu (CDN).

![Springboot-EDA](../asserts/springboot-eda.gif)

* Sources: [login.png](../asserts/login.png), [welcome.png](../asserts/welcome.png), [persons.png](../asserts/persons.png), [products.png](../asserts/products.png), [inventories.png](../asserts/inventories.png), [report.png](../asserts/report.png), [requests.png](../asserts/requests.png), [hazelcast_dashboard.png](../asserts/hazelcast_dashboard.png), [kafka_dashboard.png](../asserts/kafka_dashboard.png), [flink_dashboard.png](../asserts/flink_dashboard.png), [operations_dashboard.png](../asserts/operations_dashboard.png), [about.png](../asserts/about.png).

---

## Important

Construire une application web regroupant plusieurs services SOA (limités aux services Backend-for-Frontend) dans un seul artefact déployable n'est ni recommandé ni déconseillé; la décision dépend des exigences architecturales et des compromis. Dans cette approche, les services SOA doivent se limiter aux responsabilités Backend-for-Frontend (BFF).

Pour faciliter la mise en place, ce projet intègre Kafka, Flink, Hazelcast et une base de données HSQLDB directement dans l'environnement de l'application, à des fins de démonstration. Dans des architectures de production, ces composants devraient généralement être déployés et gérés comme des services externes indépendants.

Ce projet sert uniquement à des fins éducatives, expérimentales et de démonstration.

---

## Prérequis

Les logiciels suivants doivent être installés sur votre poste de travail pour construire et exécuter ce projet:

* [Node.js](https://nodejs.org/en)
* [Java SDK](https://www.oracle.com/java/technologies/downloads/)
* [Apache Maven](https://maven.apache.org/download.cgi)
* [Optionnel – Git ou téléchargement ZIP](https://git-scm.com/downloads)
* [Optionnel – IDE (p. ex. VS Code)](https://code.visualstudio.com/download)
* [Optionnel – Extension VS Code (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.volar)
* [Optionnel – Docker (pour la génération d'images)](https://www.docker.com/products/docker-desktop/)

---

## Guide d'Ingénierie Kafka: Traitement des Flux et Résilience EDA

Ce guide compile les meilleures pratiques pour concevoir, développer et exploiter des consommateurs Kafka robustes, particulièrement dans les environnements conteneurisés (EKS).

![EDA-Iceberg-Shark](../asserts/eda-shark-iceberg.png)

**Remarque:** Les requins sont nommés Bronze, Argent et Or — l'équipe de mise en œuvre de l'architecture Médaillon. 😄

### Index Thématique

* [Architecture et Conception des Topics](./architecture_et_conception_des_topics.md)
* [Cycle de Vie et Opérations](./cycle_vie_et_operations.md)
* [Sémantiques de Livraison et Fiabilité](./semantiques_livraison_et_fiabilite.md)
* [Reconnaissance et Idempotence du Consommateur](./reconnaissance_et_idempotence_consommateur.md)
* [Patrons de Persistance et Transaction](./patrons_persistance_et_transaction.md)
* [Gouvernance et Observabilité](./gouvernance_et_observabilite.md)
* [Distribution, élasticité et Arrêt dans les environnements distribués](./distribution_scale_et_arret.md)
* [Élasticité Horizontale sur EKS (KEDA + Karpenter)](./elasticite_horizontale_eks.md)
* [Vérification des Données](./verification_donnees.md)
* [Conclusion — Complexité de l'intégration: ETL, SOA & EDA (TCO vs ROI)](./conclusion_etl_soa_eda_tco_roi.md)

Références de support:

* [Schéma de Base de Données Relationnelle](./base_de_donnees.md)
* [Guides du Sandbox (Kafka / Flink / Hazelcast)](./guides_sandbox.md)
* [Guide Flink (traitement / checkpointing / DLT / pause&reprise)](./guides_flink.md)
* [Datamesh/DataFabric](./datamesh_datafabric.md)
* [Rapports d'inventaire exemple](./rapports.md)
* [Guides docker](./guides_docker.md)
* [Présentation powerpoint en format PDF](./org-rd-fullstack-springboot-eda-fr.pdf)

---

## Spring Boot – Guide de Démarrage

```bash
mvn clean                                        # Supprime les fichiers compilés et les artefacts.
mvn test                                         # Compile et exécute tous les tests (côté Java uniquement).
mvn install -DskipTests                          # Construit et empaquète l'application (Java et Nuxt).
mvn spring-boot:run                              # Démarre l'application Spring Boot.

mvn wrapper:wrapper                              # Régénère les fichiers Maven wrapper.
mvn dependency:sources                           # Télécharge les sources des dépendances.
mvn dependency:resolve -Dclassifier=javadoc      # Télécharge les Javadocs des dépendances.

mvn spring-boot:build-image                      # Construit une image OCI avec Paketo Buildpacks.
                                                 # Alternativement, utilisez le Dockerfile pour les
                                                 # constructions personnalisées.

java -jar target/springboot-eda-unspecified.jar  # Exécute directement le JAR empaqueté.

# Spring Boot layer tools
java -Djarmode=layertools \
  -jar target/springboot-eda-unspecified.jar list
                                                 # Liste les couches du JAR.

java -Djarmode=layertools \
  -jar target/springboot-eda-unspecified.jar extract \
  --destination target/tmp
                                                 # Extraire les couches du JAR dans un répertoire.
```

## Nuxt4 – Guide de Démarrage

```bash
cd src/frontend                                  # Naviguer vers les sources de l'application Nuxt.

npm i -D vuetify vite-plugin-vuetify             # Installation du plugin Vuetify pour Nuxt.
npm i @mdi/font                                  # Installation des icônes Material Design.

npm cache clean --force                          # Vider le cache NPM.
npm install                                      # Installation des dépendances du projet.
npm run dev                                      # Démarrer l'application avec le "hot reloading".
npm run preview                                  # Prévisualiser la construction du logiciel.
npm run build && npm run start                   # Construire et démarrer la version de production.
npm run generate                                 # Génération de la version SSG.

npx nuxi@latest upgrade                          # Mise à jour de la version Nuxt.
npx nuxi cleanup                                 # Supprimer les fichiers temporaires.
npm outdated                                     # Voir la liste des packages désuets.

npm set registry=https://registry.npmjs.org/     # Altérer l'adresse du registre NPM (pratique si derrière un proxy).
npm config set strict-ssl false --global         # Désactiver la vérification SSL (pas pour la production).

npm i nuxi                                       # Installer le module nuxi (optionnel).
npx nuxi init frontend                           # Créer une nouvelle application Nuxt dans le répertoire « frontend ».
```

Lorsque l'application est en cours d'exécution, les URLS suivants sont disponibles.

* [Application web Nuxt4](http://localhost:8080/app)
* [Swagger UI (test d'API)](http://localhost:8080/swagger-ui)
* [Spécification OpenAPI](http://localhost:8080/v3/api-docs)
* [Points de terminaison Actuator](http://localhost:8081/actuator)
* [Sonde d'information](http://localhost:8081/actuator/info)
* [Sonde d'état de santé](http://localhost:8081/actuator/health)
* [Sonde de vivacité (liveness)](http://localhost:8081/actuator/health/liveness)
* [Sonde de disponibilité (readiness)](http://localhost:8081/actuator/health/readiness)
* [Métriques Prometheus](http://localhost:8081/actuator/prometheus)
* [Vue d'ensemble Apache/Flink](http://localhost:{port}/overview)
* [Vue d'ensemble des jobs Apache/Flink](http://localhost:{port}/jobs/overview)
* [Détails du job Apache/Flink](http://localhost:{port}/jobs/{jobId})
* [Exceptions du job Apache/Flink](http://localhost:{port}/jobs/{jobId}/exceptions)
* [Points de contrôle (checkpoints) du job Apache/Flink](http://localhost:{port}/jobs/{jobId}/checkpoints)

---

## Docker – Guide de démarrage

```bash

# Projet docker : org-rd-fullstack/springboot-eda 

docker build --no-cache .                        # Construire une image OCI à partir du répertoire courant.
docker build --no-cache \
 -t org-rd-fullstack/springboot-eda:your-tag-name . 
                                                 # Construire et mettre une étiquette sur une image docker.
docker build --platform linux/arm64 \
 -t org-rd-fullstack/springboot-eda:arm64 .      
                                                 # Construire une image docker ARM64 (MacOS) et mettre son étiquette.

docker tag org-rd-fullstack/springboot-eda:arm64 \
 your-username/org-rd-fullstack-springboot-eda:arm64
                                                 # Mettre une étiquette pour la publication.

docker push your-username/org-rd-fullstack-springboot-eda:arm64   
                                                 # Publier une image docker à votre dépôt.

docker build --platform linux/amd64 \
 -t org-rd-fullstack/springboot-eda:amd64 .
                                                 # Construire une image docker AMD64 (Windows) et mettre son étiquette.

docker tag org-rd-fullstack/springboot-eda:amd64 \
 your-username/org-rd-fullstack-springboot-eda:amd64
                                                 # Mettre une étiquette pour la publication.

docker push username/org-rd-fullstack-springboot-eda:amd64
                                                 # Publier une image docker à votre dépôt.

docker run -it -p8080:8080 -p8081:8081 org-rd-fullstack/springboot-eda:your-tag-name   
                                                 # Exécuter l'image docker avec le mapping du port.

# Intergiciel docker : org-rd-fullstack/eda-middleware

docker buildx build --platform linux/arm64 \
 -f Dockerfile.eda-middleware -t org-rd-fullstack/eda-middleware:arm64
                                                 # Build a ARM64 (MacOS) image and tag the Docker image.

docker tag org-rd-fullstack/eda-middleware:arm64 \ 
 your-username/org-rd-fullstack-eda-middleware:arm64
                                                 # Tag the Docker image for a publication.

docker push username/org-rd-fullstack-eda-middleware:arm64
                                                 # Push the Docker image to the repository.    

docker buildx build --platform linux/amd64 \
 -f Dockerfile.eda-middleware -t org-rd-fullstack/eda-middleware:amd64

docker tag org-rd-fullstack/eda-middleware:amd64 \
 your-username/org-rd-fullstack-eda-middleware:amd64
                                                 # Tag the Docker image for a publication.

docker push username/org-rd-fullstack-eda-middleware:amd64
                                                 # Push the Docker image to the repository.

docker run -it --rm --name eda-middleware \
 -p 5432:5432 -p 9092:9092 -p 5701:5701 \
 -p 6123:6123 -p 8081:8081 \
 -e POSTGRES_PASSWORD=postgres \
 org-rd-fullstack/eda-middleware:your-tag-name
                                                 # Exécuter l'image docker avec le mapping des ports.

docker system prune -a                           # Supprimer les données Docker inutilisées (à utiliser avec précaution).
docker image ls                                  # Lister les images Docker locales.
docker rmi -f <imageID>                          # Forcer la suppression d'une image par son ID.

dive org-rd-fullstack/springboot-eda:unspecified # Inspecter les couches de l'image docker.
                                                 # See: https://github.com/wagoodman/dive.
```

---

## Docker Hub – Guide de Démarrage

* [Docker Hub](https://hub.docker.com/repositories/rdemers)
* [More information - Dockerfile](../../Dockerfile)
* [More information - Dockerfile.eda-middleware](../../Dockerfile.eda-middleware)

Les images docker sont disponibles pour ARM64(MacOS) et AMD64(Windows). Cependant, si vous désirez utiliser cette image pour faire du développement (accès aux services: kafka, flink et hazelcast), vous pouvez également activer "enable host networking" comme démontré ci-contre.

![Docker-Network-Settings](../asserts/docker-network-settings.png)

---

## Note

Les tests peuvent s'exécuter en parallèle sur certains environnements, même si l'exécution parallèle est explicitement désactivée. Pour éviter les interférences entre les exécutions de tests, assurez-vous que chaque test unitaire utilise des noms de topics uniques. Pour cette raison, certaines parties des tests unitaires actuels sont désactivées par défaut. Voir [T8700_PipelineController_UT_Tests](../../src/test/java/org/rd/fullstack/springbooteda/T8700_PipelineController_UT_Tests.java) et [T2450_FlinkPipeline_UT_Tests](../../src/test/java/org/rd/fullstack/springbooteda/T2450_FlinkPipeline_UT_Tests.java), qui peuvent entrer en conflit du fait qu'ils partagent une partie du contexte d'exécution.

## Conclusion

La fenêtre de connexion va reconnaître ces utilisateurs :

```java
@Bean
    UserUtils userUtils() {
        UserUtils userUtils = new UserUtils();
        PasswordEncoder passwordEncoder = passwordEncoder();

        userUtils.add("root", passwordEncoder.encode("root"),
                Arrays.asList(Role.ROLE_SELECT, Role.ROLE_INSERT, Role.ROLE_UPDATE, Role.ROLE_DELETE));

        userUtils.add("support", passwordEncoder.encode("support"),
                Arrays.asList(Role.ROLE_SELECT, Role.ROLE_UPDATE));

        userUtils.add("guest", passwordEncoder.encode("guest"),
                Arrays.asList(Role.ROLE_SELECT));

        return userUtils;
    }
```

Source: [`SecurityConfig`](../../src/main/java/org/rd/fullstack/springbooteda/config/SecurityConfig.java)

Amusez-vous à faire des expériences !
