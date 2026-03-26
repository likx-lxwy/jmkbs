package com.example.demo.mapper;

import com.example.demo.dto.MerchantReviewLogRow;
import com.example.demo.model.PaymentLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentLogQueryMapper {

    @Select("select id, user_id, order_number, amount, type, remark, created_at from payment_logs where user_id = #{userId} order by created_at desc")
    @Results(id = "paymentLogResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_number", property = "orderNumber"),
            @Result(column = "amount", property = "amount"),
            @Result(column = "type", property = "type"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<PaymentLog> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Select("""
            select p.id, p.user_id, p.order_number, p.amount, p.type, p.remark, p.created_at
            from payment_logs p
            join users u on u.id = p.user_id
            where upper(u.role) = 'ADMIN'
            order by p.created_at desc, p.id desc
            """)
    @Results(id = "paymentLogAdminResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "user_id", property = "user",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "order_number", property = "orderNumber"),
            @Result(column = "amount", property = "amount"),
            @Result(column = "type", property = "type"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<PaymentLog> findAdminIncome();

    @Select("select id, user_id, order_number, amount, type, remark, created_at from payment_logs where order_number = #{orderNumber} order by id asc")
    @Results(id = "paymentLogByOrderNumberResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "user_id", property = "user",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "order_number", property = "orderNumber"),
            @Result(column = "amount", property = "amount"),
            @Result(column = "type", property = "type"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<PaymentLog> findByOrderNumberOrderByIdAsc(@Param("orderNumber") String orderNumber);

    @Select("""
            select id, user_id, order_number, amount, type, remark, created_at
            from payment_logs
            where upper(type) = upper(#{type})
              and remark is not null
              and remark like concat(#{remarkPrefix}, '%')
              and concat(',', substring(remark, char_length(#{remarkPrefix}) + 1), ',')
                    like concat('%,', #{orderId}, ',%')
            order by id desc
            """)
    @Results(id = "paymentLogBatchByOrderIdResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "user_id", property = "user",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "order_number", property = "orderNumber"),
            @Result(column = "amount", property = "amount"),
            @Result(column = "type", property = "type"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<PaymentLog> findBatchLogsByOrderId(@Param("orderId") Long orderId,
                                            @Param("type") String type,
                                            @Param("remarkPrefix") String remarkPrefix);

    @Select("""
            select exists(
                select 1
                from payment_logs
                where order_number = #{orderNumber} and type = #{type}
            )
            """)
    boolean existsByOrderNumberAndType(@Param("orderNumber") String orderNumber, @Param("type") String type);

    @Insert("""
            insert into payment_logs (user_id, order_number, amount, type, remark, created_at)
            values (#{user.id}, #{orderNumber}, #{amount}, #{type}, #{remark}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(PaymentLog paymentLog);

    @Select("""
            <script>
            select distinct p.id,
                   pr.owner_id as merchant_id,
                   o.buyer_id as buyer_id,
                   u.username as buyer_username,
                   p.order_number,
                   p.remark,
                   p.created_at
            from payment_logs p
            join customer_orders o on o.order_number = p.order_number
            join order_items oi on oi.order_id = o.id
            join products pr on pr.id = oi.product_id
            join users u on u.id = o.buyer_id
            where upper(p.type) = 'REVIEW'
            <if test='merchantId != null'>
              and pr.owner_id = #{merchantId}
            </if>
            order by p.created_at desc, p.id desc
            </script>
            """)
    @Results(id = "merchantReviewLogRowResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "merchant_id", property = "merchantId"),
            @Result(column = "buyer_id", property = "buyerId"),
            @Result(column = "buyer_username", property = "buyerUsername"),
            @Result(column = "order_number", property = "orderNumber"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<MerchantReviewLogRow> findMerchantReviewLogs(@Param("merchantId") Long merchantId);
}
