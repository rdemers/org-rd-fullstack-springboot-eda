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

import java.util.List;

import org.rd.fullstack.springbooteda.dto.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    private UserUtils userUtils;

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userUtils.findByUsername(username);
        if (user == null)
            throw new UsernameNotFoundException("Utilisateur inexistant: " + username);

        return UserDetailsImpl.build(user);
    }

    public UserDetails loadUserByToken(String jwt) {
        String username = jwtUtils.getUserNameFromJwtToken(jwt);
        List<Role> roles = jwtUtils.getAuthoritiesFromJwtToken(jwt);

        User user = new User(username, null, roles);
        return UserDetailsImpl.build(user);
    }
}