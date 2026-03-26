package com.example.demo.mapper;

import com.example.demo.model.OrderItem;
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
public interface OrderItemQueryMapper {

    @Select("select id, order_id, product_id, quantity, unit_price, size_label from order_items where order_id = #{orderId} order by id asc")
    @Results(id = "orderItemResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "quantity", property = "quantity"),
            @Result(column = "unit_price", property = "unitPrice"),
            @Result(column = "size_label", property = "sizeLabel"),
            @Result(column = "product_id", property = "product",
                    one = @One(select = "com.example.demo.mapper.ProductQueryMapper.selectById"))
    })
    List<OrderItem> findByOrderId(@Param("orderId") Long orderId);

    @Insert("""
            insert into order_items (order_id, product_id, quantity, unit_price, size_label)
            values (#{order.id}, #{product.id}, #{quantity}, #{unitPrice}, #{sizeLabel})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(OrderItem orderItem);

}
