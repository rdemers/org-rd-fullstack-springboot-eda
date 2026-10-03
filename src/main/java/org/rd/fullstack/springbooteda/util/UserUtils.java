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
package org.rd.fullstack.springbooteda.util;

import java.util.ArrayList;
import java.util.List;

import org.rd.fullstack.springbooteda.dto.User;

public class UserUtils {

    private List<User> users;

    public UserUtils() {
        super();
        this.users = new ArrayList<>();
    }

    public void add(String username, String password, List<Role> roles) {
        users.add(new User(username, password, roles));
    }

    public User findByUsername(String username) {
        User findUser = 
                 users.stream()
                      .filter(user -> username.equals(user.getUsername()))
                      .findAny()
                      .orElse(null);
        return findUser;
    }
}