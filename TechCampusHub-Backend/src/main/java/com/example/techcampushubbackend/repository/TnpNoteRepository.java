package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.TnpNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TnpNoteRepository extends JpaRepository<TnpNote, Long> {

    List<TnpNote> findAllByOrderByCreatedAtDesc();
}
