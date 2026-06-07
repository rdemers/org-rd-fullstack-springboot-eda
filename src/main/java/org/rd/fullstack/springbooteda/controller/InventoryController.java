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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.InventoryView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@CrossOrigin
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "SecureAPI")
public class InventoryController {

    private static final Logger logger = 
        LoggerFactory.getLogger(InventoryController.class);

    @Autowired
    private InventoryRepository inventoryRepository;

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/inventories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the inventories list.", description = "Inventory.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No inventories."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<Inventory>> getAll(@RequestParam(name="productCode", required = false) String productCode) {
        try {
            List<Inventory> inventories = new ArrayList<>();

            if (productCode == null)
                inventories.addAll(inventoryRepository.findAll());
            else
                inventories.addAll(inventoryRepository.findByProductCode(productCode));

            if (inventories.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(inventories, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Exception getting list: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/inventories/{inventoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get an inventory by its identifier.", description = "Inventory.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown inventory."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Inventory> get(@PathVariable("inventoryId") long inventoryId) {

        try {
            Optional<Inventory> inventory = inventoryRepository.findById(inventoryId);
            return inventory.map(value ->
                    new ResponseEntity<>(value, HttpStatus.OK)).orElseGet(()
                        -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } catch (Exception ex) {
            logger.error("FindById exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_INSERT')")
    @PostMapping(value = "/inventories", consumes = MediaType.APPLICATION_JSON_VALUE,
                                         produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add a new inventory.", description = "Inventory.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Success|Created."),
        @ApiResponse(responseCode = "409", description = "Duplicate inventory."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Inventory> save(@RequestBody Inventory newInventory) {

        try {
            Inventory inventory = inventoryRepository.saveAndFlush(newInventory);
            return new ResponseEntity<>(inventory, HttpStatus.CREATED);
        } catch (DataIntegrityViolationException ex) {
            return new ResponseEntity<>(HttpStatus.CONFLICT); // 409
        } catch (Exception ex) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_UPDATE')")
    @PutMapping(value = "/inventories/{inventoryId}", consumes = MediaType.APPLICATION_JSON_VALUE,
                                                      produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a inventory.", description = "Inventory.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown inventory."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Inventory> update(@PathVariable("inventoryId") long inventoryId, @RequestBody Inventory majinventory) {
        try {
            Optional<Inventory> inventory = inventoryRepository.findById(inventoryId);
            if (inventory.isPresent()) {
                inventory.get().setInventory(majinventory);
                return new ResponseEntity<>(inventoryRepository.saveAndFlush(inventory.get()), HttpStatus.OK);
            } else
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        } catch (Exception ex) {
            logger.error("Update exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_DELETE')")
    @DeleteMapping(value = "/inventories/{inventoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a inventory.", description = "inventory.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted completed."),
        @ApiResponse(responseCode = "404", description = "Unknown inventory."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> delete(@PathVariable("inventoryId") long inventoryId) {
        try {
            Optional<Inventory> inventory = inventoryRepository.findById(inventoryId);
            if (inventory.isEmpty())
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);

            inventoryRepository.deleteById(inventoryId);
            inventoryRepository.flush();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception ex) {
            logger.error("Delete exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_DELETE')")
    @DeleteMapping(value = "/inventories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Destroy all inventories.", description = "inventory.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted all inventories."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> deleteAll() {
        try {
            inventoryRepository.deleteAll();
            inventoryRepository.flush();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception ex) {
            logger.error("Delete all exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/inventories/view/{inventoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get an inventory by its identifier.", description = "Inventory.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown inventory."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<InventoryView> getView(@PathVariable("inventoryId") long inventoryId) {
        try {
            Optional<InventoryView> inventory = inventoryRepository.findByInventoryIdView(inventoryId);
            return inventory.map(value ->
                    new ResponseEntity<>(value, HttpStatus.OK)).orElseGet(()
                        -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } catch (Exception ex) {
            logger.error("FindById exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/inventories/view", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the inventories list with the product details.", description = "InventoryView.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No inventories."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<InventoryView>> getAllView() {
        try {
            List<InventoryView> inventoriesViews = new ArrayList<>();
            inventoriesViews.addAll(inventoryRepository.findAllView());

            if (inventoriesViews.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(inventoriesViews, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Get list exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_UPDATE')")
    @PostMapping(value = "/inventories/refill/{qty}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Refill every inventory by the given quantity.", description = "InventoryView.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No inventories."),
        @ApiResponse(responseCode = "400", description = "Invalid quantity (expected between 1 and 1000)."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<InventoryView>> refill(@PathVariable("qty") long qty) {
        try {
            if (qty < 1 || qty > 1000)
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

            inventoryRepository.refillAll(qty);
            inventoryRepository.flush();

            List<InventoryView> inventoriesViews = new ArrayList<>(inventoryRepository.findAllView());

            if (inventoriesViews.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(inventoriesViews, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Refill exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}