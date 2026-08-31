package com.memberconnect.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.memberconnect.backend.model.Title;

@Repository
public interface TitleRepository extends JpaRepository<Title, Long> {

    /** Only the titles the Name Change entry should offer. */
    List<Title> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<Title> findByNameIgnoreCase(String name);
}
