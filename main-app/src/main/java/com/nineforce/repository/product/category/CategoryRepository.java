package com.nineforce.repository.product.category;

import com.nineforce.model.product.category.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    // Retrieve top-level category (where parent is null)
    List<Category> findByParentIsNull();
    List<Category> findByParentIsNullOrderByName();
    Optional<Category> findByNameAndParent(String name, Category parent);

    List<Category> findByNameContainingIgnoreCaseOrderByName(String namePart);

    /* --- list / search helpers --- */
    Page<Category> findByNameContainingIgnoreCase(String q, Pageable p);

    /* --- One SQL call instead of “find c” then “find c.getParent()” as two separate queries. --- */
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = :id")
    Optional<Category> findByIdWithParent(@Param("id") Long id);

    /* --- Repository – fetch full hierarchy in one query --- */
    @Query("""
        select DISTINCT c
        from Category c
        left join fetch c.parent p
        left join fetch p.parent
        """)
    List<Category> findAllWithHierarchy();

    @Query("""
    SELECT DISTINCT c
    FROM Category c
    LEFT JOIN FETCH c.parent p
    LEFT JOIN FETCH p.parent
    WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
""")
    List<Category> findByNameContainingIgnoreCaseWithHierarchy(@Param("q") String q);

}