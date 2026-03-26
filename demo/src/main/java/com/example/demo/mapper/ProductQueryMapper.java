package com.example.demo.mapper;

import com.example.demo.model.Product;
import org.apache.ibatis.annotations.Delete;
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

import java.util.List;

@Mapper
public interface ProductQueryMapper {

    String BASE_COLUMNS = "id, name, description, sizes, price, image_url, stock, category_id, owner_id, video_url, sales_count";

    @Select("select " + BASE_COLUMNS + " from products where id = #{id}")
    @Results(id = "productResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "name", property = "name"),
            @Result(column = "description", property = "description"),
            @Result(column = "sizes", property = "sizes"),
            @Result(column = "price", property = "price"),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "stock", property = "stock"),
            @Result(column = "video_url", property = "videoUrl"),
            @Result(column = "sales_count", property = "salesCount"),
            @Result(column = "category_id", property = "category",
                    one = @One(select = "com.example.demo.mapper.CategoryQueryMapper.selectById")),
            @Result(column = "owner_id", property = "owner",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "id", property = "sizesDetail",
                    many = @Many(select = "com.example.demo.mapper.ProductSizeQueryMapper.findByProductId"))
    })
    Product selectById(@Param("id") Long id);

    @Select("select " + BASE_COLUMNS + " from products order by id asc")
    @Results(id = "productListResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "name", property = "name"),
            @Result(column = "description", property = "description"),
            @Result(column = "sizes", property = "sizes"),
            @Result(column = "price", property = "price"),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "stock", property = "stock"),
            @Result(column = "video_url", property = "videoUrl"),
            @Result(column = "sales_count", property = "salesCount"),
            @Result(column = "category_id", property = "category",
                    one = @One(select = "com.example.demo.mapper.CategoryQueryMapper.selectById")),
            @Result(column = "owner_id", property = "owner",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "id", property = "sizesDetail",
                    many = @Many(select = "com.example.demo.mapper.ProductSizeQueryMapper.findByProductId"))
    })
    List<Product> selectAll();

    @Select("select " + BASE_COLUMNS + " from products where category_id = #{categoryId} order by id asc")
    @Results(id = "productByCategoryResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "name", property = "name"),
            @Result(column = "description", property = "description"),
            @Result(column = "sizes", property = "sizes"),
            @Result(column = "price", property = "price"),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "stock", property = "stock"),
            @Result(column = "video_url", property = "videoUrl"),
            @Result(column = "sales_count", property = "salesCount"),
            @Result(column = "category_id", property = "category",
                    one = @One(select = "com.example.demo.mapper.CategoryQueryMapper.selectById")),
            @Result(column = "owner_id", property = "owner",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "id", property = "sizesDetail",
                    many = @Many(select = "com.example.demo.mapper.ProductSizeQueryMapper.findByProductId"))
    })
    List<Product> selectByCategoryId(@Param("categoryId") Long categoryId);

    @Select("select " + BASE_COLUMNS + " from products where owner_id = #{ownerId} order by id asc")
    @Results(id = "productByOwnerResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "name", property = "name"),
            @Result(column = "description", property = "description"),
            @Result(column = "sizes", property = "sizes"),
            @Result(column = "price", property = "price"),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "stock", property = "stock"),
            @Result(column = "video_url", property = "videoUrl"),
            @Result(column = "sales_count", property = "salesCount"),
            @Result(column = "category_id", property = "category",
                    one = @One(select = "com.example.demo.mapper.CategoryQueryMapper.selectById")),
            @Result(column = "owner_id", property = "owner",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "id", property = "sizesDetail",
                    many = @Many(select = "com.example.demo.mapper.ProductSizeQueryMapper.findByProductId"))
    })
    List<Product> selectByOwnerId(@Param("ownerId") Long ownerId);

    @Select("select count(*) from products")
    long countAll();

    @Select("select count(*) from products where stock <= #{stock}")
    long countByStockLessThanEqual(@Param("stock") Integer stock);

    @Insert("""
            insert into products (
                name, description, sizes, price, image_url, stock, category_id, owner_id, video_url, sales_count
            ) values (
                #{name}, #{description}, #{sizes}, #{price}, #{imageUrl}, #{stock},
                #{category.id}, #{owner.id}, #{videoUrl}, #{salesCount}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Product product);

    @Update("""
            update products
            set name = #{name},
                description = #{description},
                sizes = #{sizes},
                price = #{price},
                image_url = #{imageUrl},
                stock = #{stock},
                category_id = #{category.id},
                owner_id = #{owner.id},
                video_url = #{videoUrl},
                sales_count = #{salesCount}
            where id = #{id}
            """)
    int update(Product product);

    @Update("""
            update products
            set sales_count = greatest(0, coalesce(sales_count, 0) + #{delta})
            where id = #{id}
            """)
    int updateSalesCountDelta(@Param("id") Long id, @Param("delta") Long delta);

    @Delete("delete from products where id = #{id}")
    int deleteById(@Param("id") Long id);
}
