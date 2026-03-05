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

import java.util.Optional;

import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestProcessorSrv implements Processor {
    private static final Logger logger = 
        LoggerFactory.getLogger(RequestProcessorSrv.class);

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public void process(String params) throws Exception {
        // Get the request.
        Request request = JsonMapper.readFromJson(params, Request.class);

        // Check state -- Must be PENDING or BACK_ORDER.
        if ((request.getResult() != Result.PENDING) &&
            (request.getResult() != Result.BACK_ORDER))
            return;

        logger.info(request.toString());

        // Get inventory for the product.
        Optional<Inventory> inventoryOptional = inventoryRepository.findByProductId(request.getProductId());
        if (!inventoryOptional.isPresent()) { // Big problem here.
            request.setResult(Result.ERROR);
            requestRepository.save(request);
            requestRepository.flush();
            return;
        }

        Inventory inventory = inventoryOptional.get();
        Operation oper = request.getOperation();
        switch (oper) {
            case CREDIT: // Subtract from inventory.
                if (inventory.getQty() < request.getQty()) { // Not enough.
                    request.setResult(Result.BACK_ORDER);
                    requestRepository.save(request);
                    requestRepository.flush();
                    return;
                }

                inventoryRepository.creditQTY(request.getQty(), inventory.getInventoryId());
                inventoryRepository.flush();

                request.setResult(Result.EXECUTED);
                requestRepository.save(request);
                requestRepository.flush();
                break;

            case DEBIT: // Add to inventory.
                inventoryRepository.debitQTY(request.getQty(), inventory.getInventoryId());
                inventoryRepository.flush();

                request.setResult(Result.EXECUTED);
                requestRepository.save(request);
                requestRepository.flush();
                break;

            default:
                logger.info("Unimplemented operation.");
                request.setResult(Result.ERROR);
                requestRepository.save(request);
                requestRepository.flush();
        }
    }
}