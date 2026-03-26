package com.example.demo.mapper;

import com.example.demo.model.UserAddress;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserAddressQueryMapper {

    @Select("""
            select id, user_id, recipient_name, phone, address, is_default, created_at
            from user_addresses
            where user_id = #{userId}
            order by is_default desc, created_at desc
            """)
    @Results(id = "userAddressResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "recipient_name", property = "recipientName"),
            @Result(column = "phone", property = "phone"),
            @Result(column = "address", property = "address"),
            @Result(column = "is_default", property = "default"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "user_id", property = "user",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById"))
    })
    List<UserAddress> findByUserIdOrderByIsDefaultDescCreatedAtDesc(@Param("userId") Long userId);

    @Select("""
            select id, user_id, recipient_name, phone, address, is_default, created_at
            from user_addresses
            where id = #{id} and user_id = #{userId}
            limit 1
            """)
    @Results(id = "userAddressByIdResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "recipient_name", property = "recipientName"),
            @Result(column = "phone", property = "phone"),
            @Result(column = "address", property = "address"),
            @Result(column = "is_default", property = "default"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "user_id", property = "user",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById"))
    })
    UserAddress findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Insert("""
            insert into user_addresses (user_id, recipient_name, phone, address, is_default, created_at)
            values (#{user.id}, #{recipientName}, #{phone}, #{address}, #{default}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(UserAddress userAddress);

    @Update("""
            update user_addresses
            set recipient_name = #{recipientName},
                phone = #{phone},
                address = #{address},
                is_default = #{default},
                created_at = #{createdAt}
            where id = #{id}
            """)
    int update(UserAddress userAddress);

    @Delete("delete from user_addresses where id = #{id}")
    int deleteById(@Param("id") Long id);
}
