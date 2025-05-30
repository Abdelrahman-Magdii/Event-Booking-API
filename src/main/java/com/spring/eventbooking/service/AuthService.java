package com.spring.eventbooking.service;

import com.spring.eventbooking.dto.Request.LoginRequest;
import com.spring.eventbooking.dto.Request.RegisterRequest;
import com.spring.eventbooking.dto.Response.JwtResponse;
import com.spring.eventbooking.entity.Role;
import com.spring.eventbooking.entity.User;
import com.spring.eventbooking.exception.GlobalException;
import com.spring.eventbooking.mail.Services.EmailService;
import com.spring.eventbooking.mail.template.WelcomeEmailContext;
import com.spring.eventbooking.repository.RoleRepository;
import com.spring.eventbooking.repository.UserRepository;
import com.spring.eventbooking.security.JwtUtilsUser;
import com.spring.eventbooking.utiles.GlobalFunction;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtilsUser jwtUtils;
    private final EmailService emailService;


    @Autowired
    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, JwtUtilsUser jwtUtils, EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.emailService = emailService;
    }

    @Transactional
    public ResponseEntity<JwtResponse> register(RegisterRequest request, boolean isAdmin) throws MessagingException {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new GlobalException(
                    GlobalFunction.getMS("email.already.use")
                    , HttpStatus.BAD_REQUEST);
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new GlobalException("role.user.notFound", HttpStatus.NOT_FOUND));
        roles.add(userRole);

        if (isAdmin) {
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new GlobalException("role.admin.notFound", HttpStatus.NOT_FOUND));
            roles.add(adminRole);
        }

        user.setRoles(roles);
        userRepository.save(user);

        if (!isAdmin) {
            WelcomeEmailContext context = new WelcomeEmailContext();
            context.init(user);
            emailService.sendMail(context);
        }

        return getResponseEntity(user);
    }

    public ResponseEntity<JwtResponse> login(LoginRequest loginRequest) {

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new GlobalException(GlobalFunction.getMS("user.not.found"), HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new GlobalException(GlobalFunction.getMS("password.invalid"), HttpStatus.BAD_REQUEST);
        }

        return getResponseEntity(user);
    }

    ResponseEntity<JwtResponse> getResponseEntity(User user) {


        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);
        String jwt = jwtUtils.generateToken(claims, user);

        JwtResponse authResponse = new JwtResponse(jwt,
                user.getId(), user.getEmail(),
                user.getFirstName(), user.getLastName(), roles);


        return ResponseEntity.ok(authResponse);
    }

    public Optional<User> authByToken(String token) {
        try {
            String email = jwtUtils.extractEmail(token);
            Optional<User> userOptional = userRepository.findByEmail(email);

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                User userDetails = new User();
                userDetails.setEmail(user.getEmail());
                userDetails.setFirstName(user.getFirstName());
                userDetails.setLastName(user.getLastName());
                userDetails.setPassword(passwordEncoder.encode(user.getPassword()));

                Set<Role> roles = user.getRoles().stream()
                        .map(role -> new Role(role.getName()))
                        .collect(Collectors.toSet());

                userDetails.setRoles(roles);

                if (jwtUtils.isTokenValid(token, userDetails)) {
                    return userOptional;
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}