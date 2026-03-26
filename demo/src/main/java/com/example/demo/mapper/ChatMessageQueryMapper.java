package com.example.demo.mapper;

import com.example.demo.model.ChatMessage;
import org.apache.ibatis.annotations.Delete;
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
public interface ChatMessageQueryMapper {

    @Select("""
            select id, sender_id, receiver_id, product_id, content, created_at
            from chat_messages
            where product_id = #{productId}
              and ((sender_id = #{userId} and receiver_id = #{targetId})
                   or (sender_id = #{targetId} and receiver_id = #{userId}))
            order by created_at asc
            """)
    @Results(id = "chatMessageResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "content", property = "content"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "sender_id", property = "sender",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "receiver_id", property = "receiver",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "product_id", property = "product",
                    one = @One(select = "com.example.demo.mapper.ProductQueryMapper.selectById"))
    })
    List<ChatMessage> findConversation(@Param("productId") Long productId,
                                       @Param("userId") Long userId,
                                       @Param("targetId") Long targetId);

    @Select("""
            select id, sender_id, receiver_id, product_id, content, created_at
            from chat_messages
            where sender_id = #{userId} or receiver_id = #{userId}
            order by created_at desc
            """)
    @Results(id = "chatRecentResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "content", property = "content"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "sender_id", property = "sender",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "receiver_id", property = "receiver",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "product_id", property = "product",
                    one = @One(select = "com.example.demo.mapper.ProductQueryMapper.selectById"))
    })
    List<ChatMessage> findRecent(@Param("userId") Long userId);

    @Insert("""
            insert into chat_messages (sender_id, receiver_id, product_id, content, created_at)
            values (#{sender.id}, #{receiver.id}, #{product.id}, #{content}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ChatMessage chatMessage);

    @Delete("delete from chat_messages where product_id = #{productId}")
    int deleteByProductId(@Param("productId") Long productId);
}
