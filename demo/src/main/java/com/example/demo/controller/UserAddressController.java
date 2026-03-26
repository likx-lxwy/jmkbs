package com.example.demo.controller;

import com.example.demo.mapper.UserAddressQueryMapper;
import com.example.demo.model.User;
import com.example.demo.model.UserAddress;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@CrossOrigin(origins = "*")
public class UserAddressController {

    private final UserAddressQueryMapper userAddressQueryMapper;

    public UserAddressController(UserAddressQueryMapper userAddressQueryMapper) {
        this.userAddressQueryMapper = userAddressQueryMapper;
    }

    @GetMapping
    public List<UserAddress> list(HttpServletRequest request) {
        User user = currentUser(request);
        return userAddressQueryMapper.findByUserIdOrderByIsDefaultDescCreatedAtDesc(user.getId());
    }

    @PostMapping
    public UserAddress create(@RequestBody UserAddress body, HttpServletRequest request) {
        User user = currentUser(request);
        validate(body);
        UserAddress address = new UserAddress();
        address.setUser(user);
        address.setRecipientName(body.getRecipientName());
        address.setPhone(body.getPhone());
        address.setAddress(body.getAddress());
        address.setDefault(body.isDefault());
        if (address.isDefault()) {
            clearDefault(user.getId());
        }
        userAddressQueryMapper.insert(address);
        return address;
    }

    @PutMapping("/{id}")
    public UserAddress update(@PathVariable Long id, @RequestBody UserAddress body, HttpServletRequest request) {
        User user = currentUser(request);
        validate(body);
        UserAddress exist = userAddressQueryMapper.findByIdAndUserId(id, user.getId());
        if (exist == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在");
        }
        exist.setRecipientName(body.getRecipientName());
        exist.setPhone(body.getPhone());
        exist.setAddress(body.getAddress());
        boolean toDefault = body.isDefault();
        exist.setDefault(toDefault);
        if (toDefault) {
            clearDefault(user.getId());
            exist.setDefault(true);
        }
        userAddressQueryMapper.update(exist);
        return exist;
    }

    @PostMapping("/{id}/default")
    public void setDefault(@PathVariable Long id, HttpServletRequest request) {
        User user = currentUser(request);
        UserAddress exist = userAddressQueryMapper.findByIdAndUserId(id, user.getId());
        if (exist == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在");
        }
        clearDefault(user.getId());
        exist.setDefault(true);
        userAddressQueryMapper.update(exist);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, HttpServletRequest request) {
        User user = currentUser(request);
        UserAddress exist = userAddressQueryMapper.findByIdAndUserId(id, user.getId());
        if (exist == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在");
        }
        userAddressQueryMapper.deleteById(exist.getId());
        if (exist.isDefault()) {
            List<UserAddress> rest = userAddressQueryMapper.findByUserIdOrderByIsDefaultDescCreatedAtDesc(user.getId());
            if (!rest.isEmpty()) {
                UserAddress first = rest.get(0);
                first.setDefault(true);
                userAddressQueryMapper.update(first);
            }
        }
    }

    private void clearDefault(Long userId) {
        List<UserAddress> list = userAddressQueryMapper.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
        for (UserAddress address : list) {
            if (address.isDefault()) {
                address.setDefault(false);
                userAddressQueryMapper.update(address);
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
