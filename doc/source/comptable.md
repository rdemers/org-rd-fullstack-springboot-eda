# Pratique

Bonne question, mais attention — la réponse standard en comptabilité est **l'inverse** de ce que tu décris, et ton propre code fait aussi l'inverse. Laisse-moi vérifier le code pour être précis.

Non, c'est **l'inverse** — autant en comptabilité standard que dans ton propre code.

## En comptabilité (partie double)

L'inventaire est un compte d'**actif**. Pour un compte d'actif :
- **Débit → augmente** le solde
- **Crédit → diminue** le solde

Donc : *je débite → j'ajoute à l'inventaire ; je crédite → je soustrais.* C'est exactement l'opposé de ta formulation.

(La règle générale : actifs et charges augmentent au débit ; passifs, capitaux propres et produits augmentent au crédit.)

## Ce que fait ton code — et il est cohérent avec la règle

[InventoryRepository.java](src/main/java/org/rd/fullstack/springbooteda/dao/InventoryRepository.java) :

```java
// debitQTY → qty + :qty  → AUGMENTE
SET inv.qty = (inv.qty + :qty)   // debitQTY

// creditQTY → qty - :qty  → DIMINUE
SET inv.qty = (inv.qty - :qty)   // creditQTY
```

Et [PipelineProcessorSrv.java](src/main/java/org/rd/fullstack/springbooteda/srv/PipelineProcessorSrv.java) le commente explicitement :

```java
case CREDIT: // Subtract from inventory.
    ...
    inventoryRepository.creditQTY(...);   // soustrait

case DEBIT:  // Add to inventory.
    ...
    inventoryRepository.debitQTY(...);    // ajoute
```

## Résumé

| Opération | Effet sur l'inventaire (actif) |
|---|---|
| **Débit** | **+ augmente** |
| **Crédit** | **− diminue** |

Ton code est correct vis-à-vis de la comptabilité. C'est juste l'énoncé de ta question qui avait inversé les deux. 👍

À noter : le cas `CREDIT` vérifie d'abord `inventory.getQty() < request.getQty()` et bascule en `BACK_ORDER` si le stock est insuffisant — logique, puisque c'est l'opération qui retire du stock.

# Comptabilité

Parfait, content que ça serve! 🎓 

Pour tes étudiants, le moyen mnémotechnique classique en anglais est **« DEAD CLIC »** :
- **DEAD** : **D**ébit augmente → **E**xpenses (charges), **A**ssets (actifs), **D**ividends
- **CLIC** : **C**rédit augmente → **L**iabilities (passifs), **I**ncome (produits), **C**apital

L'inventaire étant un actif (le « A » de DEAD), il augmente au débit.

Bonne formation, et n'hésite pas si tu as d'autres cas à coder! 👋