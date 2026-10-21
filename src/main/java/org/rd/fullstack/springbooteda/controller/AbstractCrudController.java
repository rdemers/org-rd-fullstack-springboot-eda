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
package org.rd.fullstack.springbooteda.controller;

import java.util.Optional;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Shared save/update/delete/deleteAll logic for the entity controllers (Person, Product,
 * Inventory, Request, JrnEvent). 
 * 
 * All DataIntegrityViolationException handling is fully offloaded to the GlobalExceptionHandler.
 * This prevents Spring transaction context pollution and ensures clean 409 Conflict mappings 
 * for all CRUD operations without internal try-catch side effects.
 */
abstract class AbstractCrudController<T, ID> {

    protected abstract JpaRepository<T, ID> repository();
    protected abstract Logger logger();

    protected ResponseEntity<T> doSave(T newEntity) {
        T saved = repository().saveAndFlush(newEntity);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    protected ResponseEntity<T> doUpdate(ID id, T source, BiConsumer<T, T> applyUpdate) {
        Optional<T> entity = repository().findById(id);
        if (entity.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        T target = entity.get();
        applyUpdate.accept(target, source);
        
        T updated = repository().saveAndFlush(target);
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    protected ResponseEntity<HttpStatus> doDelete(ID id) {
        if (repository().findById(id).isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        repository().deleteById(id);
        repository().flush();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    protected ResponseEntity<HttpStatus> doDeleteAll() {
        repository().deleteAll();
        repository().flush();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
