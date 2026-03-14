package com.example.demo.controller;

import com.example.demo.dto.ProductUpsertRequest;
import com.example.demo.model.Category;
import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.model.ProductSize;
import com.example.demo.dto.ProductSizeRequest;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.ProductRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ProductController(ProductRepository productRepository, CategoryRepository categoryRepository, ChatMessageRepository chatMessageRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @GetMapping
    public List<Product> list(@RequestParam(required = false) Long categoryId) {
        if (categoryId != null) {
            return productRepository.findByCategoryId(categoryId);
        }
        return productRepository.findAll();
    }

    @GetMapping("/mine")
    public List<Product> mine(HttpServletRequest request) {
        User user = currentUser(request);
        if (user == null || (!"MERCHANT".equalsIgnoreCase(user.getRole()) && !"ADMIN".equalsIgnoreCase(user.getRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅商家或管理员可查看");
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return productRepository.findAll();
        }
        return productRepository.findByOwnerId(user.getId());
    }

    @GetMapping("/{id}")
    public Product detail(@PathVariable Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
    }

    @PostMapping
    public Product create(@Valid @RequestBody ProductUpsertRequest request, HttpServletRequest httpRequest) {
        User user = currentUser(httpRequest);
        ensurePermission(user);
        Category category = loadCategory(request.getCategoryId());
        Product product = new Product();
        applyProductFields(request, product, category);
        if ("MERCHANT".equalsIgnoreCase(user.getRole())) {
            product.setOwner(user);
        }
        return productRepository.save(product);
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductUpsertRequest request, HttpServletRequest httpRequest) {
        User user = currentUser(httpRequest);
        ensurePermission(user);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        ensureOwnership(user, product);
        Category category = loadCategory(request.getCategoryId());
        applyProductFields(request, product, category);
        return productRepository.save(product);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        User user = currentUser(httpRequest);
        ensurePermission(user);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        ensureOwnership(user, product);
        chatMessageRepository.deleteByProductId(product.getId());
        productRepository.delete(product);
    }

    private User currentUser(HttpServletRequest request) {
        return (User) request.getAttribute("CURRENT_USER");
    }

    private void ensurePermission(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!canManageProducts(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private Category loadCategory(Long categoryId) {
        if (categoryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类不能为空");
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类不存在"));
    }

    private void applyProductFields(ProductUpsertRequest request, Product product, Category category) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setSizes(request.getSizes());
        product.setPrice(request.getPrice());
        product.setImageUrl(request.getImageUrl());
        product.setVideoUrl(request.getVideoUrl());
        product.setCategory(category);
        product.getSizesDetail().clear();
        int totalStockFromSizes = 0;
        if (request.getSizesDetail() != null) {
            for (ProductSizeRequest s : request.getSizesDetail()) {
                if (s.getLabel() == null || s.getLabel().isBlank()) {
                    continue;
                }
                ProductSize ps = new ProductSize();
                ps.setProduct(product);
                ps.setLabel(s.getLabel().trim());
                ps.setStock(s.getStock() == null ? 0 : s.getStock());
                product.getSizesDetail().add(ps);
                totalStockFromSizes += ps.getStock();
            }
        }
        // 如果传了尺码明细，则以明细库存汇总为总库存；否则使用表单中的库存字段
        if (totalStockFromSizes > 0 || (request.getSizesDetail() != null && !request.getSizesDetail().isEmpty())) {
            product.setStock(totalStockFromSizes);
        } else {
            product.setStock(request.getStock() == null ? 0 : request.getStock());
        }
    }

    private boolean canManageProducts(User user) {
        if (user == null) {
            return false;
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return true;
        }
        return "MERCHANT".equalsIgnoreCase(user.getRole())
                && (user.getMerchantStatus() == null
                || !"BANNED".equalsIgnoreCase(user.getMerchantStatus()));
    }

    private void ensureOwnership(User user, Product product) {
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return;
        }
        if (product.getOwner() == null || !product.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作自己创建的商品");
        }
    }
}
