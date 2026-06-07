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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.PersonRepository;
import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.Person;
import org.rd.fullstack.springbooteda.dto.Product;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
public class DataGeneratorSrv {
    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository; 

    @Autowired
    private RequestRepository requestRepository;

    @Transactional
    public void genRandomRequest(int nbrRequest, long maxQty, boolean bDel) {
        if ((nbrRequest <= 0) || (maxQty <= 0))
            throw new IllegalArgumentException("Invalid parameters.");

        List<Person> lstPerson = personRepository.findAll();
        List<Product> lstProduct = productRepository.findAll();
        int countPerson = lstPerson.size(); 
        int countProduct = lstProduct.size();

        if ((countPerson <= 0) || (countProduct <= 0))
            throw new IllegalArgumentException("Invalid database data (Person, Product).");

        if (bDel) {
            requestRepository.deleteAll();
            requestRepository.flush();
        }

        int indexPerson; int indexProduct; long qty;
        for (int iii = 0; iii < nbrRequest; iii++) {
            qty          = ThreadLocalRandom.current().nextLong(maxQty)+1;
            indexPerson  = ThreadLocalRandom.current().nextInt(countPerson);
            indexProduct = ThreadLocalRandom.current().nextInt(countProduct);

            Request req = new Request(
                            lstPerson.get(indexPerson).getPersonId(),
                            lstProduct.get(indexProduct).getProductId(), 
                            qty, Operation.CREDIT, Result.PENDING);

            requestRepository.save(req);
        }
        requestRepository.flush();
    }

    @Transactional
    public void genRandomInventory(long qty, boolean bDel) {
        
        if (qty <= 0)
            throw new IllegalArgumentException("Invalid parameter.");

        if (productRepository.count() <= 0)
            throw new IllegalArgumentException("Invalid database data (Product).");

        if (bDel) {
            inventoryRepository.deleteAll();
            inventoryRepository.flush();
        }

        productRepository.findAll().forEach(product -> 
            inventoryRepository.save(new Inventory(product.getProductId(), qty)));

        inventoryRepository.flush();
    }
}