package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.request.PasswordResetRequest;
import com.bibliospace.backend.dtos.request.UserCreateRequest;
import com.bibliospace.backend.dtos.request.UserUpdateRequest;
import com.bibliospace.backend.dtos.response.UserResponse;
import com.bibliospace.backend.entities.Role;
import com.bibliospace.backend.entities.User;
import com.bibliospace.backend.exceptions.ApiException;
import com.bibliospace.backend.repositories.ReservationRepository;
import com.bibliospace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Gestión de cuentas por parte del administrador
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public static UserResponse toResponse(User user, Long reservas) {
        return UserResponse.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .email(user.getEmail())
                .rol(user.getRol().name())
                .activo(!Boolean.FALSE.equals(user.getActivo()))
                .reservas(reservas)
                .creadoEn(user.getCreadoEn())
                .build();
    }

    private static Role parseRole(String rol) {
        try {
            return Role.valueOf(rol.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("Rol inválido. Usa ROLE_ESTUDIANTE, ROLE_FUNCIONARIO o ROLE_ADMIN.");
        }
    }

    public List<UserResponse> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : reservationRepository.countGroupedByUsuario()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return userRepository.findAllByOrderByNombreAsc().stream()
                .map(u -> toResponse(u, counts.getOrDefault(u.getId(), 0L)))
                .toList();
    }

    public UserResponse create(UserCreateRequest request) {
        String email = AuthService.normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Ya existe una cuenta registrada con ese email.");
        }
        User user = userRepository.save(User.builder()
                .nombre(request.getNombre().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .rol(parseRole(request.getRol()))
                .build());
        return toResponse(user, 0L);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = find(id);
        User me = currentUserService.get();
        if (user.getId().equals(me.getId())) {
            throw ApiException.badRequest("No puedes cambiar tu propio rol ni suspender tu propia cuenta.");
        }

        Role nuevoRol = request.getRol() != null ? parseRole(request.getRol()) : user.getRol();
        boolean nuevoActivo = request.getActivo() != null ? request.getActivo() : !Boolean.FALSE.equals(user.getActivo());

        // Siempre debe quedar al menos un administrador activo
        boolean dejaDeSerAdminActivo = user.getRol() == Role.ROLE_ADMIN && !Boolean.FALSE.equals(user.getActivo())
                && (nuevoRol != Role.ROLE_ADMIN || !nuevoActivo);
        if (dejaDeSerAdminActivo && userRepository.countByRolAndActivoTrue(Role.ROLE_ADMIN) <= 1) {
            throw ApiException.conflict("Debe quedar al menos un administrador activo.");
        }

        user.setRol(nuevoRol);
        user.setActivo(nuevoActivo);
        return toResponse(user, null);
    }

    @Transactional
    public void resetPassword(Long id, PasswordResetRequest request) {
        User user = find(id);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
    }
}
