package com.example.demo.mapper;

import com.example.demo.model.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserQueryMapper {

    String BASE_COLUMNS = "id, username, password, email, role, account_status, merchant_status, merchant_store_name, merchant_contact_name, merchant_contact_phone, merchant_business_address, merchant_license_number, merchant_description, wallet_balance, subscription_paid_until";
    String NON_DELETED_CLAUSE = "(account_status is null or upper(account_status) <> 'DELETED')";

    @Select("select " + BASE_COLUMNS + " from users where id = #{id}")
    User selectById(@Param("id") Long id);

    @Select("select " + BASE_COLUMNS + " from users where username = #{username} limit 1")
    User selectByUsername(@Param("username") String username);

    @Select("select " + BASE_COLUMNS + " from users where " + NON_DELETED_CLAUSE + " order by id asc")
    List<User> selectAll();

    @Select("select " + BASE_COLUMNS + " from users order by id desc")
    List<User> selectAllIncludingDeletedOrderByIdDesc();

    @Select("select count(*) from users where " + NON_DELETED_CLAUSE)
    long countAll();

    @Select("select count(*) from users where role = #{role} and " + NON_DELETED_CLAUSE)
    long countByRole(@Param("role") String role);

    @Select("select count(*) from users where role = #{role} and merchant_status = #{merchantStatus} and " + NON_DELETED_CLAUSE)
    long countByRoleAndMerchantStatus(@Param("role") String role, @Param("merchantStatus") String merchantStatus);

    @Select("select " + BASE_COLUMNS + " from users where role = #{role} and " + NON_DELETED_CLAUSE + " order by id desc")
    List<User> selectByRoleOrderByIdDesc(@Param("role") String role);

    @Select("select " + BASE_COLUMNS + " from users where role = #{role} and merchant_status = #{merchantStatus} and " + NON_DELETED_CLAUSE + " order by id desc")
    List<User> selectByRoleAndMerchantStatusOrderByIdDesc(@Param("role") String role, @Param("merchantStatus") String merchantStatus);

    @Select("select " + BASE_COLUMNS + " from users where role = #{role} and " + NON_DELETED_CLAUSE + " order by id asc limit 1")
    User selectFirstByRoleOrderByIdAsc(@Param("role") String role);

    @Insert("""
            insert into users (username, password, email, role, account_status, merchant_status, merchant_store_name, merchant_contact_name, merchant_contact_phone, merchant_business_address, merchant_license_number, merchant_description, wallet_balance, subscription_paid_until)
            values (#{username}, #{password}, #{email}, #{role}, #{accountStatus}, #{merchantStatus}, #{merchantStoreName}, #{merchantContactName}, #{merchantContactPhone}, #{merchantBusinessAddress}, #{merchantLicenseNumber}, #{merchantDescription}, #{walletBalance}, #{subscriptionPaidUntil})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(User user);

    @Update("""
            update users
            set username = #{username},
                password = #{password},
                email = #{email},
                role = #{role},
                account_status = #{accountStatus},
                merchant_status = #{merchantStatus},
                merchant_store_name = #{merchantStoreName},
                merchant_contact_name = #{merchantContactName},
                merchant_contact_phone = #{merchantContactPhone},
                merchant_business_address = #{merchantBusinessAddress},
                merchant_license_number = #{merchantLicenseNumber},
                merchant_description = #{merchantDescription},
                wallet_balance = #{walletBalance},
                subscription_paid_until = #{subscriptionPaidUntil}
            where id = #{id}
            """)
    int update(User user);
}
