package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.model.UserAddress;
import com.example.demo.repository.UserAddressRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/addresses")
@CrossOrigin(origins = "*")
public class UserAddressController {

    private final UserAddressRepository userAddressRepository;

    public UserAddressController(UserAddressRepository userAddressRepository) {
        this.userAddressRepository = userAddressRepository;
    }

    @GetMapping
    public List<UserAddress> list(HttpServletRequest request) {
        User user = currentUser(request);
        return userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(user.getId());
    }

    @PostMapping
    public UserAddress create(@RequestBody UserAddress body, HttpServletRequest request) {
        User user = currentUser(request);
        validate(body);
        UserAddress addr = new UserAddress();
        addr.setUser(user);
        addr.setRecipientName(body.getRecipientName());
        addr.setPhone(body.getPhone());
        addr.setAddress(body.getAddress());
        addr.setDefault(body.isDefault());
        if (addr.isDefault()) {
            clearDefault(user.getId());
        }
        return userAddressRepository.save(addr);
    }

    @PutMapping("/{id}")
    public UserAddress update(@PathVariable Long id, @RequestBody UserAddress body, HttpServletRequest request) {
        User user = currentUser(request);
        validate(body);
        UserAddress exist = userAddressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在"));
        exist.setRecipientName(body.getRecipientName());
        exist.setPhone(body.getPhone());
        exist.setAddress(body.getAddress());
        boolean toDefault = body.isDefault();
        exist.setDefault(toDefault);
        if (toDefault) {
            clearDefault(user.getId());
            exist.setDefault(true);
        }
        return userAddressRepository.save(exist);
    }

    @PostMapping("/{id}/default")
    public void setDefault(@PathVariable Long id, HttpServletRequest request) {
        User user = currentUser(request);
        UserAddress exist = userAddressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在"));
        clearDefault(user.getId());
        exist.setDefault(true);
        userAddressRepository.save(exist);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, HttpServletRequest request) {
        User user = currentUser(request);
        UserAddress exist = userAddressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在"));
        userAddressRepository.delete(exist);
        // 若删除了默认地址，置第一个为默认
        if (exist.isDefault()) {
            List<UserAddress> rest = userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(user.getId());
            if (!rest.isEmpty()) {
                UserAddress first = rest.get(0);
                first.setDefault(true);
                userAddressRepository.save(first);
            }
        }
    }

    private void clearDefault(Long userId) {
        List<UserAddress> list = userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
        for (UserAddress ua : list) {
            if (ua.isDefault()) {
                ua.setDefault(false);
                userAddressRepository.save(ua);
            }
        }
    }

    private void validate(UserAddress body) {
        if (body.getRecipientName() == null || body.getRecipientName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收货人不能为空");
        }
        if (body.getPhone() == null || body.getPhone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (body.getAddress() == null || body.getAddress().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收货地址不能为空");
        }
    }

    private User currentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return user;
    }
}
