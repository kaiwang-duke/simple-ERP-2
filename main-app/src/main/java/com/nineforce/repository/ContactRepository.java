package com.nineforce.repository;

import com.nineforce.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByNameContainingOrPhoneContaining(String name, String phone);
}