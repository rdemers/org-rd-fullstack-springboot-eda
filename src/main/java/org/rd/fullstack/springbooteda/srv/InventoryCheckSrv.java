/*
 * Copyright 2026; Réal Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rd.fullstack.springbooteda.srv;

import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolates the inventory stock check in its own, independently-committed transaction.
 *
 * <p>The database runs in HSQLDB's {@code mvcc} mode under which this split is no longer 
 * necessary for the race to manifest.
 */
@Service
public class InventoryCheckSrv {

    private final InventoryRepository inventoryRepository;

    public InventoryCheckSrv(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public long readAvailableQty(Long productId) {
        return inventoryRepository.findByProductId(productId)
            .map(inv -> inv.getQty())
            .orElse(0L);
    }
}
