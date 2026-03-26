package com.example.demo.controller;

import com.example.demo.dto.ProductSizeRequest;
import com.example.demo.dto.ProductUpsertRequest;
import com.example.demo.mapper.CategoryQueryMapper;
import com.example.demo.mapper.ProductQueryMapper;
import com.example.demo.mapper.ProductSizeQueryMapper;
import com.example.demo.model.Category;
import com.example.demo.model.Product;
import com.example.demo.model.ProductSize;
import com.example.demo.model.User;
import com.example.demo.service.ProductCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductQueryMapper productQueryMapper;
    private final ProductSizeQueryMapper productSizeQueryMapper;
    private final CategoryQueryMapper categoryQueryMapper;
    private final ProductCatalogService productCatalogService;

    public ProductController(ProductQueryMapper productQueryMapper,
                             ProductSizeQueryMapper productSizeQueryMapper,
                             CategoryQueryMapper categoryQueryMapper,
                             ProductCatalogService productCatalogService) {
        this.productQueryMapper = productQueryMapper;
        this.productSizeQueryMapper = productSizeQueryMapper;
        this.categoryQueryMapper = categoryQueryMapper;
        this.productCatalogService = productCatalogService;
    }

    @GetMapping
    public List<Product> list(@RequestParam(required = false) Long categoryId) {
        return productCatalogService.listProducts(categoryId);
    }

    @GetMapping("/mine")
    public List<Product> mine(HttpServletRequest request) {
        User user = currentUser(request);
        if (user == null || (!"MERCHANT".equalsIgnoreCase(user.getRole()) && !"ADMIN".equalsIgnoreCase(user.getRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant or admin permission required");
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return productQueryMapper.selectAll();
        }
        return productQueryMapper.selectByOwnerId(user.getId());
    }

    @GetMapping("/{id}")
    public Product detail(@PathVariable Long id) {
        Product product = productCatalogService.getProductDetail(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return product;
    }

    @PostMapping
    @Transactional
    public Product create(@Valid @RequestBody ProductUpsertRequest request, HttpServletRequest httpRequest) {
        User user = currentUser(httpRequest);
        ensurePermission(user);
        Category category = loadCategory(request.getCategoryId());

        Product product = new Product();
        applyProductFields(request, product, category);
        if ("MERCHANT".equalsIgnoreCase(user.getRole())) {
            product.setOwner(user);
        }

        productQueryMapper.insert(product);
        replaceProductSizes(product);
        return productQueryMapper.selectById(product.getId());
    }

    @PutMapping("/{id}")
    @Transactional
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductUpsertRequest request, HttpServletRequest httpRequest) {
        User user = currentUser(httpRequest);
        ensurePermission(user);

        Product product = productQueryMapper.selectById(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        ensureOwnership(user, product);

        Category category = loadCategory(request.getCategoryId());
        applyProductFields(request, product, category);
        productQueryMapper.update(product);
        replaceProductSizes(product);
        return productQueryMapper.selectById(product.getId());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        User user = currentUser(httpRequest);
        ensurePermission(user);

        Product product = productQueryMapper.selectById(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        ensureOwnership(user, product);
        productQueryMapper.deleteById(product.getId());
    }

    private void replaceProductSizes(Product product) {
        productSizeQueryMapper.deleteByProductId(product.getId());
        for (ProductSize size : product.getSizesDetail()) {
            size.setProduct(product);
            productSizeQueryMapper.insert(size);
        }
    }

    private User currentUser(HttpServletRequest request) {
        return (User) request.getAttribute("CURRENT_USER");
    }

    private void ensurePermission(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please login first");
        }
        if (!canManageProducts(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
    }

    private Category loadCategory(Long categoryId) {
        if (categoryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is required");
        }
        Category category = categoryQueryMapper.selectById(categoryId);
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found");
        }
        return category;
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
            for (ProductSizeRequest sizeRequest : request.getSizesDetail()) {
                if (sizeRequest.getLabel() == null || sizeRequest.getLabel().isBlank()) {
                    continue;
                }
                ProductSize productSize = new ProductSize();
                productSize.setProduct(product);
                productSize.setLabel(sizeRequest.getLabel().trim());
                productSize.setStock(sizeRequest.getStock() == null ? 0 : sizeRequest.getStock());
                product.getSizesDetail().add(productSize);
                totalStockFromSizes += productSize.getStock();
            }
        }

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
                && "APPROVED".equalsIgnoreCase(user.getMerchantStatus());
    }

    private void ensureOwnership(User user, Product product) {
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return;
        }
        if (product.getOwner() == null || !product.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can only manage your own products");
        }
    }
}
