package com.playprobie.api.global.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.user.dao.UserRepository;
import com.playprobie.api.domain.user.domain.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
		return new CustomUserDetails(user);
	}

	@Transactional(readOnly = true)
	public UserDetails loadUserById(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
		return new CustomUserDetails(user);
	}
}
