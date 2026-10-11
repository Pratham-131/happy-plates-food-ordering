package com.foodify.foodiesapi.controller;

import com.foodify.foodiesapi.entity.UserEntity;
import com.foodify.foodiesapi.enums.UserRole;
import com.foodify.foodiesapi.io.AuthenticationRequest;
import com.foodify.foodiesapi.io.AuthenticationResponse;
import com.foodify.foodiesapi.repository.UserRepository;
import com.foodify.foodiesapi.service.AppUserDetailsService;
import com.foodify.foodiesapi.util.JwtUtil;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthenticationRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            UserEntity user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // Determine role: null or empty = USER, otherwise use the role
            UserRole userRole = user.getRole() == null ? UserRole.USER : user.getRole();

            // Only allow users WITHOUT explicit ADMIN role (null role or "USER" role)
            if (userRole == UserRole.ADMIN) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Access denied. Please use admin login.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
            final String jwtToken = jwtUtil.generateToken(userDetails, userRole);

            return ResponseEntity.ok(new AuthenticationResponse(request.getEmail(), jwtToken, userRole.name()));
        } catch (BadCredentialsException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Invalid email or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody AuthenticationRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            UserEntity user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // Only allow users with explicit ADMIN role
            if (user.getRole() != UserRole.ADMIN) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Access denied. Admin privileges required.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
            final String jwtToken = jwtUtil.generateToken(userDetails, user.getRole());

            return ResponseEntity.ok(new AuthenticationResponse(request.getEmail(), jwtToken, user.getRole().name()));
        } catch (BadCredentialsException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Invalid email or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }
}