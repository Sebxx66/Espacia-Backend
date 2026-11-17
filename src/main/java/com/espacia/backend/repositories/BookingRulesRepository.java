package com.espacia.backend.repositories;

import com.espacia.backend.entities.BookingRules;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRulesRepository extends JpaRepository<BookingRules, Long> {
}
