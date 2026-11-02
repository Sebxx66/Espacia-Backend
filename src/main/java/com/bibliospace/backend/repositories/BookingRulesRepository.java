package com.bibliospace.backend.repositories;

import com.bibliospace.backend.entities.BookingRules;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRulesRepository extends JpaRepository<BookingRules, Long> {
}
