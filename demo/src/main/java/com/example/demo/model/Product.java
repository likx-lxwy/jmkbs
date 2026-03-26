package com.example.demo.model;

import com.example.demo.dto.MerchantReviewView;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Transient;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 120)
    private String sizes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @jakarta.persistence.Lob
    @Column(name = "image_url", columnDefinition = "LONGTEXT")
    private String imageUrl;

    @Column(nullable = false)
    private Integer stock;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    @JsonIgnoreProperties({"products"})
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIgnoreProperties({"password", "walletBalance"})
    @JoinColumn(name = "owner_id")
    private User owner;

    @jakarta.persistence.Lob
    @Column(name = "video_url", columnDefinition = "LONGTEXT")
    private String videoUrl;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductSize> sizesDetail = new ArrayList<>();

    @Transient
    private BigDecimal merchantRatingAverage = BigDecimal.ZERO;

    @Transient
    private Long merchantRatingCount = 0L;

    @Column(name = "sales_count", nullable = false)
    private Long salesCount = 0L;

    @Transient
    private List<MerchantReviewView> merchantReviews = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSizes() {
        return sizes;
    }

    public void setSizes(String sizes) {
        this.sizes = sizes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public List<ProductSize> getSizesDetail() {
        return sizesDetail;
    }

    public void setSizesDetail(List<ProductSize> sizesDetail) {
        this.sizesDetail = sizesDetail;
    }

    public BigDecimal getMerchantRatingAverage() {
        return merchantRatingAverage;
    }

    public void setMerchantRatingAverage(BigDecimal merchantRatingAverage) {
        this.merchantRatingAverage = merchantRatingAverage;
    }

    public Long getMerchantRatingCount() {
        return merchantRatingCount;
    }

    public void setMerchantRatingCount(Long merchantRatingCount) {
        this.merchantRatingCount = merchantRatingCount;
    }

    public Long getSalesCount() {
        return salesCount;
    }

    public void setSalesCount(Long salesCount) {
        this.salesCount = salesCount;
    }

    public List<MerchantReviewView> getMerchantReviews() {
        return merchantReviews;
    }

    public void setMerchantReviews(List<MerchantReviewView> merchantReviews) {
        this.merchantReviews = merchantReviews;
    }
}
