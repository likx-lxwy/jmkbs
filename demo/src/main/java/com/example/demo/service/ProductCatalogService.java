package com.example.demo.service;

import com.example.demo.dto.MerchantReviewLogRow;
import com.example.demo.dto.MerchantReviewView;
import com.example.demo.mapper.PaymentLogQueryMapper;
import com.example.demo.mapper.ProductQueryMapper;
import com.example.demo.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProductCatalogService {

    private static final String REVIEW_META_PREFIX = "[[MERCHANT_REVIEW=";
    private static final String REVIEW_META_SUFFIX = "]]";

    private final ProductQueryMapper productQueryMapper;
    private final PaymentLogQueryMapper paymentLogQueryMapper;

    public ProductCatalogService(ProductQueryMapper productQueryMapper,
                                 PaymentLogQueryMapper paymentLogQueryMapper) {
        this.productQueryMapper = productQueryMapper;
        this.paymentLogQueryMapper = paymentLogQueryMapper;
    }

    public List<Product> listProducts(Long categoryId) {
        List<Product> products = categoryId == null
                ? productQueryMapper.selectAll()
                : productQueryMapper.selectByCategoryId(categoryId);
        if (products.isEmpty()) {
            return products;
        }

        Map<Long, MerchantRatingSummary> ratingsByMerchant = loadRatingsByMerchant();
        for (Product product : products) {
            applyProductSummary(product, ratingsByMerchant, false);
        }

        products.sort(Comparator
                .comparingLong(ProductCatalogService::salesOf).reversed()
                .thenComparing(Comparator.comparingLong(ProductCatalogService::productIdOf).reversed()));
        return products;
    }

    public Product getProductDetail(Long id) {
        Product product = productQueryMapper.selectById(id);
        if (product == null) {
            return null;
        }

        Long merchantId = product.getOwner() == null ? null : product.getOwner().getId();
        List<MerchantReviewView> merchantReviews = loadMerchantReviews(merchantId);
        Map<Long, MerchantRatingSummary> ratingsByMerchant = new LinkedHashMap<>();
        ratingsByMerchant.put(merchantId, summarizeMerchantReviews(merchantReviews));

        applyProductSummary(product, ratingsByMerchant, true);
        product.setMerchantReviews(merchantReviews);
        return product;
    }

    private void applyProductSummary(Product product,
                                     Map<Long, MerchantRatingSummary> ratingsByMerchant,
                                     boolean includeReviews) {
        if (product == null) {
            return;
        }

        Long merchantId = product.getOwner() == null ? null : product.getOwner().getId();
        MerchantRatingSummary ratingSummary = merchantId == null
                ? MerchantRatingSummary.empty()
                : ratingsByMerchant.getOrDefault(merchantId, MerchantRatingSummary.empty());

        product.setMerchantRatingAverage(ratingSummary.averageRating());
        product.setMerchantRatingCount(ratingSummary.ratingCount());
        if (includeReviews) {
            product.setMerchantReviews(ratingSummary.reviews());
        } else {
            product.setMerchantReviews(new ArrayList<>());
        }
    }

    private Map<Long, MerchantRatingSummary> loadRatingsByMerchant() {
        Map<Long, List<MerchantReviewView>> reviewsByMerchant = new LinkedHashMap<>();
        for (MerchantReviewLogRow row : paymentLogQueryMapper.findMerchantReviewLogs(null)) {
            MerchantReviewView review = parseMerchantReview(row);
            if (review == null || row.getMerchantId() == null) {
                continue;
            }
            reviewsByMerchant.computeIfAbsent(row.getMerchantId(), ignored -> new ArrayList<>()).add(review);
        }

        Map<Long, MerchantRatingSummary> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<MerchantReviewView>> entry : reviewsByMerchant.entrySet()) {
            result.put(entry.getKey(), summarizeMerchantReviews(entry.getValue()));
        }
        return result;
    }

    private List<MerchantReviewView> loadMerchantReviews(Long merchantId) {
        if (merchantId == null) {
            return List.of();
        }
        return paymentLogQueryMapper.findMerchantReviewLogs(merchantId).stream()
                .map(this::parseMerchantReview)
                .filter(Objects::nonNull)
                .toList();
    }

    private MerchantReviewView parseMerchantReview(MerchantReviewLogRow row) {
        if (row == null || row.getRemark() == null || row.getRemark().isBlank()) {
            return null;
        }
        String remark = row.getRemark().trim();
        if (!remark.startsWith(REVIEW_META_PREFIX)) {
            return null;
        }
        int endIndex = remark.indexOf(REVIEW_META_SUFFIX);
        if (endIndex <= REVIEW_META_PREFIX.length()) {
            return null;
        }

        try {
            int rating = Integer.parseInt(remark.substring(REVIEW_META_PREFIX.length(), endIndex));
            if (rating < 1 || rating > 5) {
                return null;
            }

            MerchantReviewView view = new MerchantReviewView();
            view.setBuyerId(row.getBuyerId());
            view.setBuyerUsername(row.getBuyerUsername());
            view.setRating(rating);
            view.setOrderNumber(row.getOrderNumber());
            view.setCreatedAt(row.getCreatedAt());

            String content = remark.substring(endIndex + REVIEW_META_SUFFIX.length()).trim();
            view.setContent(content.isEmpty() ? null : content);
            return view;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private MerchantRatingSummary summarizeMerchantReviews(List<MerchantReviewView> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return MerchantRatingSummary.empty();
        }

        BigDecimal total = BigDecimal.ZERO;
        List<MerchantReviewView> normalizedReviews = new ArrayList<>();
        for (MerchantReviewView review : reviews) {
            if (review == null || review.getRating() == null) {
                continue;
            }
            total = total.add(BigDecimal.valueOf(review.getRating()));
            normalizedReviews.add(review);
        }

        if (normalizedReviews.isEmpty()) {
            return MerchantRatingSummary.empty();
        }

        BigDecimal average = total.divide(BigDecimal.valueOf(normalizedReviews.size()), 1, RoundingMode.HALF_UP);
        return new MerchantRatingSummary(average, (long) normalizedReviews.size(), normalizedReviews);
    }

    private static long salesOf(Product product) {
        if (product == null || product.getSalesCount() == null) {
            return 0L;
        }
        return product.getSalesCount();
    }

    private static long productIdOf(Product product) {
        return product == null || product.getId() == null ? Long.MAX_VALUE : product.getId();
    }

    private record MerchantRatingSummary(BigDecimal averageRating, Long ratingCount, List<MerchantReviewView> reviews) {

        private static MerchantRatingSummary empty() {
            return new MerchantRatingSummary(BigDecimal.ZERO, 0L, List.of());
        }
    }
}
