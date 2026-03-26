package com.example.demo.mapper;

import com.example.demo.model.CustomerOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CustomerOrderQueryMapper {

    String BASE_COLUMNS = "id, order_number, customer_name, phone, address, total_amount, status, requires_approval, escrow_amount, pay_method, created_at, refund_reason, buyer_id";

    @Select("select " + BASE_COLUMNS + " from customer_orders where id = #{id}")
    @Results(id = "customerOrderResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_number", property = "orderNumber"),
            @Result(column = "customer_name", property = "customerName"),
            @Result(column = "phone", property = "phone"),
            @Result(column = "address", property = "address"),
            @Result(column = "total_amount", property = "totalAmount"),
            @Result(column = "status", property = "status"),
            @Result(column = "requires_approval", property = "requiresApproval"),
            @Result(column = "escrow_amount", property = "escrowAmount"),
            @Result(column = "pay_method", property = "payMethod"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "refund_reason", property = "refundReason"),
            @Result(column = "buyer_id", property = "buyer",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "id", property = "items",
                    many = @Many(select = "com.example.demo.mapper.OrderItemQueryMapper.findByOrderId"))
    })
    CustomerOrder selectById(@Param("id") Long id);

    @Select("select " + BASE_COLUMNS + " from customer_orders where order_number = #{orderNumber} limit 1")
    @Results(id = "customerOrderByNumberResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_number", property = "orderNumber"),
            @Result(column = "customer_name", property = "customerName"),
            @Result(column = "phone", property = "phone"),
            @Result(column = "address", property = "address"),
            @Result(column = "total_amount", property = "totalAmount"),
            @Result(column = "status", property = "status"),
            @Result(column = "requires_approval", property = "requiresApproval"),
            @Result(column = "escrow_amount", property = "escrowAmount"),
            @Result(column = "pay_method", property = "payMethod"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "refund_reason", property = "refundReason"),
            @Result(column = "buyer_id", property = "buyer",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "id", property = "items",
                    many = @Many(select = "com.example.demo.mapper.OrderItemQueryMapper.findByOrderId"))
    })
    CustomerOrder selectByOrderNumber(@Param("orderNumber") String orderNumber);

    @Select("select " + BASE_COLUMNS + " from customer_orders order by created_at desc")
    @Results(id = "customerOrderListResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_number", property = "orderNumber"),
            @Result(column = "customer_name", property = "customerName"),
            @Result(column = "phone", property = "phone"),
            @Result(column = "address", property = "address"),
            @Result(column = "total_amount", property = "totalAmount"),
            @Result(column = "status", property = "status"),
            @Result(column = "requires_approval", property = "requiresApproval"),
            @Result(column = "escrow_amount", property = "escrowAmount"),
            @Result(column = "pay_method", property = "payMethod"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "refund_reason", property = "refundReason"),
            @Result(column = "buyer_id", property = "buyer",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "id", property = "items",
                    many = @Many(select = "com.example.demo.mapper.OrderItemQueryMapper.findByOrderId"))
    })
    List<CustomerOrder> selectAllOrderByCreatedAtDesc();

    @Select("select " + BASE_COLUMNS + " from customer_orders where buyer_id = #{buyerId} order by created_at desc")
    @Results(id = "customerOrderByBuyerResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_number", property = "orderNumber"),
            @Result(column = "customer_name", property = "customerName"),
            @Result(column = "phone", property = "phone"),
            @Result(column = "address", property = "address"),
            @Result(column = "total_amount", property = "totalAmount"),
            @Result(column = "status", property = "status"),
            @Result(column = "requires_approval", property = "requiresApproval"),
            @Result(column = "escrow_amount", property = "escrowAmount"),
            @Result(column = "pay_method", property = "payMethod"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "refund_reason", property = "refundReason"),
            @Result(column = "buyer_id", property = "buyer",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "id", property = "items",
                    many = @Many(select = "com.example.demo.mapper.OrderItemQueryMapper.findByOrderId"))
    })
    List<CustomerOrder> selectByBuyerIdOrderByCreatedAtDesc(@Param("buyerId") Long buyerId);

    @Select("""
            select distinct o.id, o.order_number, o.customer_name, o.phone, o.address, o.total_amount, o.status,
                   o.requires_approval, o.escrow_amount, o.pay_method, o.created_at, o.refund_reason, o.buyer_id
            from customer_orders o
            join order_items i on i.order_id = o.id
            join products p on p.id = i.product_id
            where p.owner_id = #{ownerId}
            order by o.created_at desc
            """)
    @Results(id = "customerOrderByOwnerResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_number", property = "orderNumber"),
            @Result(column = "customer_name", property = "customerName"),
            @Result(column = "phone", property = "phone"),
            @Result(column = "address", property = "address"),
            @Result(column = "total_amount", property = "totalAmount"),
            @Result(column = "status", property = "status"),
            @Result(column = "requires_approval", property = "requiresApproval"),
            @Result(column = "escrow_amount", property = "escrowAmount"),
            @Result(column = "pay_method", property = "payMethod"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "refund_reason", property = "refundReason"),
            @Result(column = "buyer_id", property = "buyer",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "id", property = "items",
                    many = @Many(select = "com.example.demo.mapper.OrderItemQueryMapper.findByOrderId"))
    })
    List<CustomerOrder> selectByProductOwnerIdOrderByCreatedAtDesc(@Param("ownerId") Long ownerId);

    @Select("select count(*) from customer_orders")
    long countAll();

    @Select("select coalesce(sum(total_amount), 0) from customer_orders")
    BigDecimal sumTotalAmount();

    @Insert("""
            insert into customer_orders (
                order_number, customer_name, phone, address, total_amount, status,
                requires_approval, escrow_amount, pay_method, created_at, refund_reason, buyer_id
            ) values (
                #{orderNumber}, #{customerName}, #{phone}, #{address}, #{totalAmount}, #{status},
                #{requiresApproval}, #{escrowAmount}, #{payMethod}, #{createdAt}, #{refundReason}, #{buyer.id}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(CustomerOrder customerOrder);

    @Update("""
            update customer_orders
            set order_number = #{orderNumber},
                customer_name = #{customerName},
                phone = #{phone},
                address = #{address},
                total_amount = #{totalAmount},
                status = #{status},
                requires_approval = #{requiresApproval},
                escrow_amount = #{escrowAmount},
                pay_method = #{payMethod},
                created_at = #{createdAt},
                refund_reason = #{refundReason},
                buyer_id = #{buyer.id}
            where id = #{id}
            """)
    int update(CustomerOrder customerOrder);
}
