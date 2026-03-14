package com.example.demo.repository;

import com.example.demo.model.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    boolean existsByOrderNumber(String orderNumber);

    @Query("select coalesce(sum(o.totalAmount), 0) from CustomerOrder o")
    BigDecimal sumTotalAmount();

    @Query("select distinct o from CustomerOrder o left join fetch o.items items order by o.createdAt desc")
    List<CustomerOrder> findAllWithItemsOrderByCreatedAtDesc();

    List<CustomerOrder> findByStatusOrderByCreatedAtDesc(String status);

    List<CustomerOrder> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    @Query("select distinct o from CustomerOrder o join o.items i join i.product p where p.owner.id = :ownerId order by o.createdAt desc")
    List<CustomerOrder> findByProductOwnerIdOrderByCreatedAtDesc(Long ownerId);

    java.util.Optional<CustomerOrder> findByOrderNumber(String orderNumber);
}
