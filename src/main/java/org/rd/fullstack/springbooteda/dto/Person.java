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
package org.rd.fullstack.springbooteda.dto;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "person")
public class Person {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "person_id", unique = true, nullable = false)
    private Long personId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    public Person() {
        super();
        version   = null;
        personId  = null;
        firstName = null;
        lastName  = null;
        balance   = null;
    }

    public Person(String firstName, String lastName, BigDecimal balance) {
        this();
        this.firstName = firstName;
        this.lastName = lastName;
        this.balance = balance;
    }

    public Long getVersion() {
        return this.version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
    
    public Long getPersonId() {
        return this.personId;
    }

    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    public String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return this.lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public BigDecimal getBalance() {
        return this.balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void setPerson(Person person) {
        // personId and version are intentionally left untouched: personId is the managed
        // entity's primary key (already correct from findById) and version is maintained by
        // Hibernate's optimistic locking — neither must be overwritten by the update body.
        this.firstName = person.getFirstName();
        this.lastName  = person.getLastName();
        this.balance   = person.getBalance();
    }

    @Override
    public String toString() {
        return "Person [personId=" + this.personId +
               ", firstName=" + this.firstName +
               ", lastName=" + this.lastName +
               ", balance=" + String.valueOf(this.balance) +
               ", version=" + this.version + "]";
    }
}