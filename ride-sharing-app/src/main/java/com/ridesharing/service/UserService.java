package com.ridesharing.service;

import com.ridesharing.dto.request.LoginRequest;
import com.ridesharing.dto.request.RegisterRequest;
import com.ridesharing.dto.response.AuthResponse;
import com.ridesharing.dto.response.UserResponse;
import com.ridesharing.entity.User;
import com.ridesharing.enums.RideStatus;
import com.ridesharing.enums.UserRole;
import com.ridesharing.exception.BadRequestException;
import com.ridesharing.exception.ResourceNotFoundException;
import com.ridesharing.repository.RatingRepository;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service handling user registration, authentication, and profile management.
 * Also implements Spring Security's UserDetailsService for JWT authentication.
 */
@Service
public class UserService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RideRepository rideRepository;
    private final RatingRepository ratingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public UserService(UserRepository userRepository,
                       RideRepository rideRepository,
                       RatingRepository ratingRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       @Lazy
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.rideRepository = rideRepository;
        this.ratingRepository = ratingRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
    }

    /** Load user by email for Spring Security authentication */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    /**
     * Register a new user (rider or driver) in the system.
     * Validates email and phone uniqueness before creating the account.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        logger.info("Registering new user with email: {}", request.getEmail());

        // Validate email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        // Validate phone number uniqueness
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BadRequestException("Phone number is already registered");
        }

        // Create and save the new user
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .role(request.getRole())
                .active(true)
                .build();

        user = userRepository.save(user);
        logger.info("User registered successfully with ID: {}", user.getId());

        // Generate JWT token for the newly registered user
        String token = jwtTokenProvider.generateTokenFromEmail(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }

    /**
     * Authenticate a user and return a JWT token.
     * Validates credentials using Spring Security's AuthenticationManager.
     */
    public AuthResponse login(LoginRequest request) {
        logger.info("Login attempt for email: {}", request.getEmail());

        // Authenticate using Spring Security
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        // Generate JWT token on successful authentication
        String token = jwtTokenProvider.generateToken(authentication);

        User user = (User) authentication.getPrincipal();

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }

    /** Get user profile by ID with computed fields like average rating and total rides */
    public UserResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return mapToUserResponse(user);
    }

    /** Get the profile of the currently authenticated user */
    public UserResponse getCurrentUserProfile(User currentUser) {
        return mapToUserResponse(currentUser);
    }

    /** Get all users with a specific role (e.g., all drivers) */
    public List<UserResponse> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role).stream()
                .map(this::mapToUserResponse)
                .toList();
    }

    /** Map User entity to UserResponse DTO with computed rating and ride count */
    private UserResponse mapToUserResponse(User user) {
        // Calculate average rating for the user
        Double averageRating = ratingRepository.findAverageRatingByUserId(user.getId());

        // Count total completed rides based on user role
        Long totalRides;
        if (user.getRole() == UserRole.DRIVER) {
            totalRides = rideRepository.countByDriverIdAndStatus(user.getId(), RideStatus.COMPLETED);
        } else {
            totalRides = rideRepository.countByRiderIdAndStatus(user.getId(), RideStatus.COMPLETED);
        }

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .active(user.getActive())
                .averageRating(averageRating != null ? averageRating : 0.0)
                .totalRides(totalRides.intValue())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
