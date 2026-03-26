package com.example.demo.mapper;

import com.example.demo.model.ProductComment;
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
public interface ProductCommentQueryMapper {

    @Select("""
            select id, product_id, user_id, content, created_at
            from product_comments
            where product_id = #{productId}
            order by created_at asc
            """)
    @Results(id = "productCommentResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "content", property = "content"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "product_id", property = "product",
                    one = @One(select = "com.example.demo.mapper.ProductQueryMapper.selectById")),
            @Result(column = "user_id", property = "user",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById"))
    })
    List<ProductComment> findByProductIdOrderByCreatedAtAsc(@Param("productId") Long productId);

    @Insert("""
            insert into product_comments (product_id, user_id, content, created_at)
            values (#{product.id}, #{user.id}, #{content}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ProductComment productComment);
}
