package com.example.demo.mapper;

import com.example.demo.model.RefundChatMessage;
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
public interface RefundChatMessageQueryMapper {

    @Select("""
            select id, order_id, sender_id, receiver_id, content, created_at
            from refund_chat_messages
            where order_id = #{orderId}
            order by created_at asc, id asc
            """)
    @Results(id = "refundChatMessageResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "content", property = "content"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "sender_id", property = "sender",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById")),
            @Result(column = "receiver_id", property = "receiver",
                    one = @One(select = "com.example.demo.mapper.UserQueryMapper.selectById"))
    })
    List<RefundChatMessage> findByOrderId(@Param("orderId") Long orderId);

    @Select("select count(*) from refund_chat_messages where order_id = #{orderId}")
    long countByOrderId(@Param("orderId") Long orderId);

    @Insert("""
            insert into refund_chat_messages (order_id, sender_id, receiver_id, content, created_at)
            values (#{order.id}, #{sender.id}, #{receiver.id}, #{content}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(RefundChatMessage refundChatMessage);
}
