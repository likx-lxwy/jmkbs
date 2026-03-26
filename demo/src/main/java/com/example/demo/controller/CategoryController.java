package com.example.demo.controller;

import com.example.demo.mapper.CategoryQueryMapper;
import com.example.demo.model.Category;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryQueryMapper categoryQueryMapper;

    public CategoryController(CategoryQueryMapper categoryQueryMapper) {
        this.categoryQueryMapper = categoryQueryMapper;
    }

    @GetMapping
    public List<Category> list() {
        return categoryQueryMapper.selectAll();
    }
}
