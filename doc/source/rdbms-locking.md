# Database locking

## **Mécanismes ACID**

Les SGBD assurent l’intégrité via les propriétés :

* **Atomicité** : tout ou rien.
* **Cohérence** : les contraintes doivent toujours être respectées.
* **Isolation** : les transactions concurrentes ne se perturbent pas.
* **Durabilité** : les données validées ne sont jamais perdues.

---

## 1. **Verrouillage (Locking)**

Mécanisme fondamental pour éviter les conflits entre transactions concurrentes.

### a. **Verrouillage pessimiste (Pessimistic Locking)**

* L’enregistrement est **verrouillé dès qu’une transaction le lit ou le modifie**.
* Empêche les autres transactions de le modifier tant que le verrou n’est pas libéré.
* Exemple SQL :

  ```sql
  SELECT * FROM clients WHERE id = 10 FOR UPDATE;
  ```

* **Avantage :** garantit une cohérence stricte.
* **Inconvénient :** peut réduire la concurrence (bloquages possibles).

### b. **Verrouillage optimiste (Optimistic Locking)**

* Chaque enregistrement comporte un **champ de version (ex. version, timestamp)**.
* Lors de la mise à jour, le SGBD vérifie que la version n’a pas changé.
* Exemple conceptuel :

  ```sql
  UPDATE produit 
  SET prix = 100, version = version + 1 
  WHERE id = 5 AND version = 3;
  ```

  → Si aucune ligne n’est mise à jour, c’est qu’un autre utilisateur a modifié l’enregistrement entre-temps.
* **Avantage :** très bon pour les applications à forte lecture et peu de conflits.
* **Inconvénient :** nécessite une gestion d’erreurs côté application.

---
UPDATE your_table_name
SET numeric_column_name = numeric_column_name + 1;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

public class MyService {
    // ... EntityManager injection

    public List<Integer> getDifferences() {
        String jpql = "SELECT e.value2 - e.value1 FROM MyEntity e";
        TypedQuery<Integer> query = entityManager.createQuery(jpql, Integer.class);
        return query.getResultList();
    }
}

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    @Modifying // Indique que c'est une opération de modification (UPDATE ou DELETE)
    @Transactional // Nécessaire pour les opérations de modification
    @Query("UPDATE Utilisateur u SET u.email = :email WHERE u.id = :id")
    int mettreAJourEmailParId(@Param("email") String email, @Param("id") Long id);
}

While there is no strict, hard-coded "maximum" number of partitions for a single Kafka topic, practical limits exist based on various factors within a Kafka cluster.
Key considerations for partition limits:
Per-Broker Partition Limit: A general guideline suggests a maximum of around 4,000 partitions per broker, including leader and follower replicas. Exceeding this can lead to performance issues, particularly related to Zookeeper (in older Kafka versions) and leader election.
Cluster-Wide Partition Limit: While not a hard limit, a Kafka cluster can typically support up to 200,000 partitions in total (distributed across all topics and brokers). KRaft, a newer Kafka consensus protocol, aims to significantly increase this to potentially 2 million partitions.
Resource Consumption: Each partition consumes resources on the broker, including memory for buffers and file descriptors. A large number of partitions can lead to increased resource consumption and potential out-of-memory errors if not properly managed.
Replication Factor: The replication factor directly impacts the number of partitions that need to be managed. A higher replication factor means more replicas of each partition, increasing the overall partition count within the cluster.
Consumer Group Rebalancing: A large number of partitions can impact the time it takes for consumer group rebalances to complete, potentially leading to increased latency during consumer join/leave events.
Broker Failure Recovery: In the event of a broker failure, the time required to elect new leaders for all affected partitions increases with the number of partitions.
In summary:
While Kafka technically allows for a high number of partitions per topic, it is crucial to consider the practical implications and resource constraints of your specific Kafka cluster and workload. It is generally recommended to carefully determine the optimal number of partitions based on factors like consumer throughput, message ordering requirements, and cluster capacity, rather than aiming for the absolute maximum.
