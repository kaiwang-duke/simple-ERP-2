package com.nineforce.controller;

import com.nineforce.model.Contact;
import com.nineforce.repository.ContactRepository;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;
import java.util.Optional;

/**
 * Controller class for handling contact related requests
 * REST controll and JS frontend. This is newer approach than thyme leaf.
 */

@RestController
@RequestMapping("/contacts")
public class ContactController {

    @Autowired
    private ContactRepository repository;

    @GetMapping
    public List<Contact> getAllContacts() {
        return repository.findAll();
    }

    @GetMapping("/search")
    public List<Contact> searchContacts(@RequestParam String keyword) {
        return repository.findByNameContainingOrPhoneContaining(keyword, keyword);
    }

    @PostMapping
    public Contact createContact(@RequestBody Contact contact) {
        return repository.save(contact);
    }

    @PutMapping("/{id}")
    public Contact updateContact(@PathVariable Long id, @RequestBody Contact contactDetails) {
        Optional<Contact> optionalContact = repository.findById(id);
        if (optionalContact.isPresent()) {
            Contact contact = optionalContact.get();
            contact.setName(contactDetails.getName());
            contact.setPhone(contactDetails.getPhone());
            return repository.save(contact);
        }
        throw new RuntimeException("Contact not found");
    }

    @DeleteMapping("/{id}")
    public void deleteContact(@PathVariable Long id) {
        repository.deleteById(id);
    }
}