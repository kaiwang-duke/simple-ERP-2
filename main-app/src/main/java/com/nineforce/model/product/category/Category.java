package com.nineforce.model.product.category;

import com.nineforce.service.product.category.CategoryPathUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(name = "cn_name")
    private String cnName;

    @Column(name = "is_seasonal", nullable = false)
    private boolean isSeasonal;

    // These were previously LocalDate — now stored as integers (1–12)
    @Column(name = "start_selling_month")
    private Integer startSellingMonth;

    @Column(name = "stop_selling_month")
    private Integer stopSellingMonth;

    @Column(name = "purchase_month")
    private Integer purchaseMonth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Category> children;

    public void setName(String name) {
        this.name = (name == null || name.trim().isEmpty()) ? "" : name.trim();
    }


    public void setCnName(String cnName) {
        this.cnName = (cnName == null || cnName.trim().isEmpty()) ? "" : cnName.trim();
    }

    public boolean isSeasonal() {
        return isSeasonal;
    }

    public void setSeasonal(boolean seasonal) {
        isSeasonal = seasonal;
    }


    /* ------------------------------------------------------------
    * Convenience: "Grand -> Parent -> Self"
    * Delegates to one canonical utility method so every layer
    * (UI list, Excel diff, tests) builds the same path string.
    * ------------------------------------------------------------ */
    @Transient
    public String getPath() {
        return CategoryPathUtil.pathOf(this);
    }
}