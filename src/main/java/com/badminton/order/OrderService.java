package com.badminton.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.badminton.product.Product;
import com.badminton.product.ProductRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepo;

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(Integer id) {
        return orderRepository.findById(id).orElse(null);
    }
    
    public List<Order> getOrdersByMemberId(Integer memberId) {
        return orderRepository.findByMember_MemberId(memberId);
    }

    public List<OrderItem> getItemsByOrderId(Integer orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    public void saveOrder(Order order) {
        orderRepository.save(order);
    }

    public void saveOrderItem(OrderItem orderItem) {
        // subtotal 自動計算 = quantity × unitPrice (對應 V2 的 OrderItemDAO 邏輯)
        orderItem.setSubtotal(orderItem.getQuantity() * orderItem.getUnitPrice());
        // ① 從資料庫查出商品
        Product product = productRepo.findById(orderItem.getProduct().getProductId()).orElseThrow(
        	    () -> new RuntimeException("找不到商品"));
        // ② 檢查庫存夠不夠
        if(product.getStockQty() < orderItem.getQuantity()) {
        	throw new RuntimeException("庫存不足！剩餘：" + product.getStockQty());
        }
        // ③ 扣庫存
        product.setStockQty(product.getStockQty() - orderItem.getQuantity());
        productRepo.save(product);
        
        orderItemRepository.save(orderItem);
        
        // 新增明細後，重新計算訂單總金額
        recalcOrderTotal(orderItem.getOrderId());
    }

    public OrderItem getOrderItemById(Integer itemId) {
        return orderItemRepository.findById(itemId).orElse(null);
    }

    // 更新單筆明細 (對應 V2 的 OrderItemDAO.updateItem)
    // ★ 修正：更新時自動處理庫存差額（商品變更 / 數量變更）
    public void updateOrderItem(Integer itemId, Product product, Integer quantity, Integer unitPrice) {
        OrderItem item = orderItemRepository.findById(itemId).orElse(null);
        if (item != null) {
            // ① 記錄舊的商品與數量
            Product oldProduct = item.getProduct();
            Integer oldQuantity = item.getQuantity();
            Integer oldProductId = (oldProduct != null) ? oldProduct.getProductId() : null;
            Integer newProductId = (product != null) ? product.getProductId() : null;

            // ② 判斷商品是否有更換
            boolean productChanged = (oldProductId != null && newProductId != null && !oldProductId.equals(newProductId));

            if (productChanged) {
                // 商品更換：舊商品回補全部數量，新商品扣除全部數量
                Product oldProd = productRepo.findById(oldProductId).orElse(null);
                if (oldProd != null) {
                    oldProd.setStockQty(oldProd.getStockQty() + oldQuantity);
                    productRepo.save(oldProd);
                }
                Product newProd = productRepo.findById(newProductId).orElseThrow(
                        () -> new RuntimeException("找不到新商品"));
                if (newProd.getStockQty() < quantity) {
                    throw new RuntimeException("新商品庫存不足！剩餘：" + newProd.getStockQty());
                }
                newProd.setStockQty(newProd.getStockQty() - quantity);
                productRepo.save(newProd);
            } else if (!quantity.equals(oldQuantity) && oldProductId != null) {
                // 同一商品，數量變更：只調整差額
                int diff = quantity - oldQuantity; // 正數=多買, 負數=少買
                Product prod = productRepo.findById(oldProductId).orElse(null);
                if (prod != null) {
                    if (diff > 0 && prod.getStockQty() < diff) {
                        throw new RuntimeException("庫存不足！剩餘：" + prod.getStockQty() + "，需額外扣除：" + diff);
                    }
                    prod.setStockQty(prod.getStockQty() - diff); // diff 為負時等於回補
                    productRepo.save(prod);
                }
            }

            // ③ 更新明細欄位
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setSubtotal(quantity * unitPrice); // subtotal 由後端自動計算
            orderItemRepository.save(item);
            // 更新明細後，重新計算訂單總金額
            recalcOrderTotal(item.getOrderId());
        }
    }

    // 刪除單筆明細 (對應 V2 的 OrderItemDAO.deleteByItemId)
    // ★ 修正：刪除明細時自動回補該筆商品的庫存
    public void deleteOrderItem(Integer itemId) {
        OrderItem item = orderItemRepository.findById(itemId).orElse(null);
        if (item != null) {
            Integer orderId = item.getOrderId();

            // ★ 回補庫存：將被刪除的明細數量加回商品庫存
            if (item.getProduct() != null) {
                Product product = productRepo.findById(item.getProduct().getProductId()).orElse(null);
                if (product != null) {
                    product.setStockQty(product.getStockQty() + item.getQuantity());
                    productRepo.save(product);
                }
            }

            orderItemRepository.deleteById(itemId);
            // 刪除明細後，重新計算訂單總金額
            recalcOrderTotal(orderId);
        }
    }

    // 重新計算訂單總金額 (對應 V2 的 OrderItemActionServlet.recalcOrderTotal)
    private void recalcOrderTotal(Integer orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            int total = items.stream()
                    .mapToInt(i -> i.getQuantity() * i.getUnitPrice())
                    .sum();
            order.setTotalAmount(total);
            orderRepository.save(order);
        }
    }

    /**
     * ★ 回補庫存：將訂單中所有明細的數量加回商品庫存
     * 用於訂單取消或刪除時，確保商品庫存不會永久流失
     */
    private void restoreStock(Integer orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            if (item.getProduct() != null) {
                Product product = productRepo.findById(item.getProduct().getProductId()).orElse(null);
                if (product != null) {
                    product.setStockQty(product.getStockQty() + item.getQuantity());
                    productRepo.save(product);
                }
            }
        }
    }

    /**
     * ★ 重新扣庫存：當訂單從「已取消」恢復為其他狀態時，重新扣除庫存
     * 防止管理員誤操作取消→恢復導致庫存虛增
     */
    private void deductStock(Integer orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            if (item.getProduct() != null) {
                Product product = productRepo.findById(item.getProduct().getProductId()).orElse(null);
                if (product != null) {
                    if (product.getStockQty() < item.getQuantity()) {
                        throw new RuntimeException(
                            "無法恢復訂單：商品「" + product.getProductName() + "」庫存不足！" +
                            "剩餘：" + product.getStockQty() + "，需要：" + item.getQuantity());
                    }
                    product.setStockQty(product.getStockQty() - item.getQuantity());
                    productRepo.save(product);
                }
            }
        }
    }

    // 更新訂單 (對應原版 V2 的 OrderDAO.updateOrder 方法)
    // 更新 status, paymentType, note，並在狀態變更時自動記錄時間點
    public void updateOrder(Integer id, OrderStatus status, PaymentType paymentType, String note) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order != null) {
            // ★ 狀態變更時，自動記錄對應的時間點
            OrderStatus oldStatus = order.getStatus();
            if (oldStatus != status) {
                LocalDateTime now = LocalDateTime.now();
                switch (status) {
                    case PAID      -> order.setPaidAt(now);
                    case SHIPPED   -> order.setShippedAt(now);
                    case COMPLETED -> order.setCompletedAt(now);
                    case CANCELLED -> order.setCancelledAt(now);
                    default -> {}  // UNPAID 不需要額外時間（用 created_at）
                }

                // ★ 取消訂單時，自動回補庫存
                if (status == OrderStatus.CANCELLED && oldStatus != OrderStatus.CANCELLED) {
                    restoreStock(id);
                }

                // ★ 從「已取消」恢復為其他狀態時，重新扣除庫存（防止庫存虛增）
                if (oldStatus == OrderStatus.CANCELLED && status != OrderStatus.CANCELLED) {
                    deductStock(id);
                }
            }

            order.setStatus(status);
            order.setPaymentType(paymentType);
            order.setNote(note);
            orderRepository.save(order); // JPA 的 save() 偵測到已有 ID，會自動執行 UPDATE
        }
    }

    public void deleteOrder(Integer id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return;

        // ★ 刪除非取消狀態的訂單時，自動回補庫存（已取消的訂單在取消時就已經回補過了）
        if (order.getStatus() != OrderStatus.CANCELLED) {
            restoreStock(id);
        }

        // 在刪除訂單前，必須先刪除底下的明細，否則會發生資料庫 FK (Foreign Key) 衝突錯誤
        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        orderItemRepository.deleteAll(items);
        
        // 明細都刪除乾淨後，再刪除訂單主體
        orderRepository.deleteById(id);
    }
}
