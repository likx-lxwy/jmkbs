package com.example.demo.mapper;

import com.example.demo.model.ProductSize;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductSizeQueryMapper {

    @Select("select id, product_id, label, stock from product_sizes where product_id = #{productId} order by id asc")
    @Results(id = "productSizeResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "label", property = "label"),
            @Result(column = "stock", property = "stock")
    })
    List<ProductSize> findByProductId(@Param("productId") Long productId);

    @Insert("""
            insert into product_sizes (product_id, label, stock)
            values (#{product.id}, #{label}, #{stock})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ProductSize productSize);

    @Update("""
            update product_sizes
            set label = #{label},
                stock = #{stock}
            where id = #{id}
            """)
    int update(ProductSize productSize);

    @Delete("delete from product_sizes where product_id = #{productId}")
    int deleteByProductId(@Param("productId") Long productId);
}
