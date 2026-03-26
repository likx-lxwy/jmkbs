package com.example.demo.mapper;

import com.example.demo.model.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CategoryQueryMapper {

    @Select("select id, name from categories where id = #{id}")
    Category selectById(@Param("id") Long id);

    @Select("select id, name from categories order by id asc")
    List<Category> selectAll();
}
