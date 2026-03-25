package com.leviathanledger.leviathan.security;

import com.leviathanledger.leviathan.model.User;
import com.leviathanledger.leviathan.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Front-end sends email as the primary login ID
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Vault Error: User not found with email: " + email));

        return UserDetailsImpl.build(user);
    }
}