using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.EntityFrameworkCore;
using OrderService.Models;

namespace OrderService.Services
{
    /// <summary>
    /// Business logic for order management.
    /// Contains all business rules for order lifecycle.
    /// </summary>
    public class OrderService
    {
        private readonly OrderDbContext _context;
        private readonly ILogger<OrderService> _logger;

        // BR-ORDER-005: Orders exceeding this threshold require supervisor approval
        private const decimal ApprovalThreshold = 1000m;

        // BR-ORDER-004: VIP customer discount percentage
        private const decimal VipDiscountPercent = 0.15m;

        // BR-ORDER-011: Maximum items per order
        private const int MaxItemsPerOrder = 50;

        public OrderService(OrderDbContext context, ILogger<OrderService> logger)
        {
            _context = context;
            _logger = logger;
        }

        /// <summary>
        /// Create a new order with full business rule validation.
        /// </summary>
        public async Task<OrderResult> CreateOrderAsync(CreateOrderRequest request)
        {
            var customer = await _context.Customers.FindAsync(request.CustomerId);
            if (customer == null)
                return OrderResult.Failure("Customer not found");

            // BR-ORDER-007: Orders must have at least one line item
            if (request.Items == null || !request.Items.Any())
                return OrderResult.Failure("Order must have at least one line item");

            // BR-ORDER-011: Maximum items per order
            if (request.Items.Count > MaxItemsPerOrder)
                return OrderResult.Failure($"Order cannot have more than {MaxItemsPerOrder} items");

            // BR-ORDER-009: Validate each line item quantity >= 1
            // BR-ORDER-010: Validate each line item unit price > 0
            foreach (var item in request.Items)
            {
                if (item.Quantity < 1)
                    return OrderResult.Failure($"Item '{item.ProductName}' quantity must be at least 1");
                if (item.UnitPrice <= 0)
                    return OrderResult.Failure($"Item '{item.ProductName}' price must be greater than 0");
            }

            // Calculate total
            decimal subtotal = request.Items.Sum(i => i.Quantity * i.UnitPrice);

            // BR-ORDER-004: Apply VIP discount (15%)
            decimal discount = 0m;
            if (customer.IsVip)
            {
                discount = subtotal * VipDiscountPercent;
                _logger.LogInformation("Applied VIP discount of {Discount} for customer {CustomerId}",
                    discount, customer.Id);
            }

            decimal totalAmount = subtotal - discount;

            // BR-ORDER-001: Order amount must be > 0
            if (totalAmount <= 0)
                return OrderResult.Failure("Order total amount must be greater than 0");

            // BR-ORDER-006: Check customer credit limit
            if (customer.CurrentBalance + totalAmount > customer.CreditLimit)
                return OrderResult.Failure(
                    $"Order exceeds credit limit. Current balance: {customer.CurrentBalance}, " +
                    $"Order total: {totalAmount}, Credit limit: {customer.CreditLimit}");

            // BR-ORDER-008: Shipping address required for physical goods
            if (request.HasPhysicalGoods && string.IsNullOrWhiteSpace(request.ShippingAddress))
                return OrderResult.Failure("Shipping address is required for orders with physical goods");

            var order = new Order
            {
                OrderNumber = GenerateOrderNumber(),
                CustomerId = request.CustomerId,
                TotalAmount = totalAmount,
                Status = OrderStatus.Created,
                CreatedAt = DateTime.UtcNow,
                ShippingAddress = request.ShippingAddress,
                Notes = request.Notes,
                LineItems = request.Items.Select(i => new OrderLineItem
                {
                    ProductName = i.ProductName,
                    Quantity = i.Quantity,
                    UnitPrice = i.UnitPrice
                }).ToList()
            };

            // BR-ORDER-005: Orders over threshold require supervisor approval
            if (totalAmount > ApprovalThreshold)
            {
                order.RequiresApproval = true;
                _logger.LogInformation("Order {OrderNumber} requires supervisor approval (total: {Total})",
                    order.OrderNumber, totalAmount);
            }

            _context.Orders.Add(order);
            await _context.SaveChangesAsync();

            // Update customer balance
            customer.CurrentBalance += totalAmount;
            await _context.SaveChangesAsync();

            return OrderResult.Success(order);
        }

        /// <summary>
        /// Retrieve an order by ID.
        /// </summary>
        public async Task<Order> GetOrderAsync(int orderId)
        {
            return await _context.Orders
                .Include(o => o.LineItems)
                .Include(o => o.Customer)
                .FirstOrDefaultAsync(o => o.Id == orderId);
        }

        /// <summary>
        /// Transition order to Confirmed status.
        /// </summary>
        public async Task<OrderResult> ConfirmOrderAsync(int orderId, int? approvedById = null)
        {
            var order = await _context.Orders.FindAsync(orderId);
            if (order == null)
                return OrderResult.Failure("Order not found");

            // BR-ORDER-002: State transition Created → Confirmed only
            if (order.Status != OrderStatus.Created)
                return OrderResult.Failure(
                    $"Cannot confirm order in '{order.Status}' status. Must be in 'Created' status.");

            // BR-ORDER-005: Check supervisor approval if required
            if (order.RequiresApproval && approvedById == null)
                return OrderResult.Failure("Order requires supervisor approval. Provide approvedById.");

            if (order.RequiresApproval)
            {
                order.ApprovedById = approvedById;
                order.ApprovedAt = DateTime.UtcNow;
            }

            order.Status = OrderStatus.Confirmed;
            order.ConfirmedAt = DateTime.UtcNow;
            await _context.SaveChangesAsync();

            return OrderResult.Success(order);
        }

        /// <summary>
        /// Transition order to Shipped status.
        /// </summary>
        public async Task<OrderResult> ShipOrderAsync(int orderId)
        {
            var order = await _context.Orders.FindAsync(orderId);
            if (order == null)
                return OrderResult.Failure("Order not found");

            // BR-ORDER-002: State transition Confirmed → Shipped only
            if (order.Status != OrderStatus.Confirmed)
                return OrderResult.Failure(
                    $"Cannot ship order in '{order.Status}' status. Must be in 'Confirmed' status.");

            order.Status = OrderStatus.Shipped;
            order.ShippedAt = DateTime.UtcNow;
            await _context.SaveChangesAsync();

            return OrderResult.Success(order);
        }

        /// <summary>
        /// Transition order to Delivered status.
        /// </summary>
        public async Task<OrderResult> DeliverOrderAsync(int orderId)
        {
            var order = await _context.Orders.FindAsync(orderId);
            if (order == null)
                return OrderResult.Failure("Order not found");

            // BR-ORDER-002: State transition Shipped → Delivered only
            if (order.Status != OrderStatus.Shipped)
                return OrderResult.Failure(
                    $"Cannot deliver order in '{order.Status}' status. Must be in 'Shipped' status.");

            order.Status = OrderStatus.Delivered;
            order.DeliveredAt = DateTime.UtcNow;
            await _context.SaveChangesAsync();

            return OrderResult.Success(order);
        }

        /// <summary>
        /// Cancel an order. Only allowed before shipping.
        /// </summary>
        public async Task<OrderResult> CancelOrderAsync(int orderId, string reason)
        {
            var order = await _context.Orders
                .Include(o => o.Customer)
                .FirstOrDefaultAsync(o => o.Id == orderId);

            if (order == null)
                return OrderResult.Failure("Order not found");

            // BR-ORDER-003: Cancellation only allowed before Shipped
            if (order.Status == OrderStatus.Shipped ||
                order.Status == OrderStatus.Delivered)
            {
                return OrderResult.Failure(
                    $"Cannot cancel order in '{order.Status}' status. " +
                    "Cancellation is only allowed before shipping.");
            }

            if (order.Status == OrderStatus.Cancelled)
                return OrderResult.Failure("Order is already cancelled");

            // Refund: reduce customer balance
            if (order.Customer != null)
            {
                order.Customer.CurrentBalance -= order.TotalAmount;
            }

            order.Status = OrderStatus.Cancelled;
            order.CancelledAt = DateTime.UtcNow;
            order.Notes = string.IsNullOrEmpty(order.Notes)
                ? $"Cancelled: {reason}"
                : $"{order.Notes}\nCancelled: {reason}";

            await _context.SaveChangesAsync();

            return OrderResult.Success(order);
        }

        private string GenerateOrderNumber()
        {
            return $"ORD-{DateTime.UtcNow:yyyyMMdd}-{Guid.NewGuid().ToString("N")[..8].ToUpper()}";
        }
    }

    // --- Request / Result types ---

    public class CreateOrderRequest
    {
        public int CustomerId { get; set; }
        public List<CreateOrderItemRequest> Items { get; set; }
        public string ShippingAddress { get; set; }
        public bool HasPhysicalGoods { get; set; } = true;
        public string Notes { get; set; }
    }

    public class CreateOrderItemRequest
    {
        public string ProductName { get; set; }
        public int Quantity { get; set; }
        public decimal UnitPrice { get; set; }
    }

    public class OrderResult
    {
        public bool IsSuccess { get; set; }
        public string ErrorMessage { get; set; }
        public Order Order { get; set; }

        public static OrderResult Success(Order order) =>
            new() { IsSuccess = true, Order = order };

        public static OrderResult Failure(string message) =>
            new() { IsSuccess = false, ErrorMessage = message };
    }
}
