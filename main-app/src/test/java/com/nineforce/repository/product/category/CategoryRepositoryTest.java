package com.nineforce.repository.product.category;

import com.nineforce.model.product.category.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/*
@DataJpaTest(
        excludeAutoConfiguration = {
                FlywayAutoConfiguration.class,
                SqlInitializationAutoConfiguration.class
        },
        properties = {
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.sql.init.mode=never",                      // <- do not run schema.sql/data.sql
                "spring.jpa.properties.hibernate.hbm2ddl.import_files=" // <- stops Hibernate from running import.sql
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)

 */

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Sql(statements = "TRUNCATE TABLE categories RESTART IDENTITY CASCADE", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)

class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository repo;

    private Category create(String name, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setCnName(name);
        c.setParent(parent);
        return c;
    }

    @Test
    @DisplayName("findAllWithHierarchy fetch-joins parent and grandparent")
    void findAllWithHierarchy_shouldFetchTwoLevels() {
        Category grand  = repo.save(create("Grand",  null));
        Category parent = repo.save(create("Parent", grand));
        repo.save(create("Child",  parent));   // Child of Parent, Parent of Grand

        List<Category> all = repo.findAllWithHierarchy();

        Category fetchedChild = all.stream()
                .filter(c -> "Child".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(fetchedChild.getParent()).isNotNull();
        assertThat(fetchedChild.getParent().getName()).isEqualTo("Parent");
        assertThat(fetchedChild.getParent().getParent()).isNotNull();
        assertThat(fetchedChild.getParent().getParent().getName()).isEqualTo("Grand");
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCaseWithHierarchy does case-insensitive search + fetch-joins")
    void findByNameContainingIgnoreCaseWithHierarchy_shouldSearchAndFetch() {
        Category top = repo.save(create("Top", null));
        repo.save(create("Sub", top));  // Top -> Sub hierarchy

        List<Category> result = repo.findByNameContainingIgnoreCaseWithHierarchy("su");

        assertThat(result)
                .extracting(Category::getName)
                .containsExactly("Sub");
        Category fetched = result.get(0);
        assertThat(fetched.getParent()).isNotNull();
        assertThat(fetched.getParent().getName()).isEqualTo("Top");
    }

    @Test
    @DisplayName("findByIdWithParent loads a single parent in one query")
    void findByIdWithParent_shouldLoadParent() {
        Category p = repo.save(create("P", null));
        Category c = repo.save(create("C", p));

        Optional<Category> opt = repo.findByIdWithParent(c.getId().longValue());
        assertThat(opt).isPresent();
        Category loaded = opt.get();
        assertThat(loaded.getName()).isEqualTo("C");
        assertThat(loaded.getParent()).isNotNull();
        assertThat(loaded.getParent().getName()).isEqualTo("P");
    }

    // --- added ---
    @Test
    @DisplayName("findByParentIsNull returns only root category")
    void findByParentIsNull_shouldReturnRootOnly() {
        Category root1 = repo.save(create("Root1", null));
        repo.save(create("Root2", null));
        Category child = repo.save(create("Child", root1));

        List<Category> roots = repo.findByParentIsNull();

        assertThat(roots)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Root1", "Root2");
        assertThat(roots).doesNotContain(child);
    }

    // --- added ---
    @Test
    @DisplayName("findByParentIsNullOrderByName returns roots sorted by name")
    void findByParentIsNullOrderByName_shouldSortRoots() {
        repo.save(create("Beta", null));
        Category a = repo.save(create("Alpha", null));
        repo.save(create("Gamma", a));  // a child, should be excluded

        List<Category> sorted = repo.findByParentIsNullOrderByName();

        assertThat(sorted)
                .extracting(Category::getName)
                .containsExactly("Alpha", "Beta");
    }

    // --- added ---
    @Test
    @DisplayName("findByNameAndParent finds the correct category under given parent")
    void findByNameAndParent_shouldFindUnderParent() {
        Category p1 = repo.save(create("Parent1", null));
        Category p2 = repo.save(create("Parent2", null));
        Category child1 = repo.save(create("Child", p1));
        repo.save(create("Child", p2));

        Optional<Category> opt = repo.findByNameAndParent("Child", p1);
        assertThat(opt).isPresent();
        assertThat(opt.get().getParent().getName()).isEqualTo("Parent1");
        assertThat(opt.get().getId()).isEqualTo(child1.getId());
    }

    // --- added ---
    @Test
    @DisplayName("findByNameContainingIgnoreCaseOrderByName does case-insensitive search + order")
    void findByNameContainingIgnoreCaseOrderByName_shouldSearchAndOrder() {
        repo.save(create("apple", null));
        repo.save(create("Banana", null));
        repo.save(create("apricot", null));

        List<Category> result = repo.findByNameContainingIgnoreCaseOrderByName("ap");

        assertThat(result)
                .extracting(Category::getName)
                .containsExactly("apple", "apricot");
    }

    // --- added ---
    @Test
    @DisplayName("findByNameContainingIgnoreCase pageable works")
    void findByNameContainingIgnoreCase_pageable() {
        // create 5 matching, 2 non-matching
        for (int i = 1; i <= 5; i++) {
            repo.save(create("match" + i, null));
        }
        repo.save(create("other1", null));
        repo.save(create("other2", null));

        // page size 2
        Page<Category> page1 = repo.findByNameContainingIgnoreCase("match", PageRequest.of(0, 2));
        Page<Category> page2 = repo.findByNameContainingIgnoreCase("match", PageRequest.of(1, 2));
        Page<Category> page3 = repo.findByNameContainingIgnoreCase("match", PageRequest.of(2, 2));

        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page2.getContent()).hasSize(2);
        assertThat(page3.getContent()).hasSize(1);
    }
    // --- end added ---
}
