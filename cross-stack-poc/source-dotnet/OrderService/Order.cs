using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace OrderService.Models
{
    /// <summary>
    /// Order entity with Entity Framework data annotations.
    /// Contains business rules encoded as validation attributes.
    /// </summary>
    [Table("Orders")]
    public class Order
    {
        [Key]
        [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
        public int Id { get; set; }

        [Required]
        [StringLength(50)]
        public string OrderNumber { get; set; }

        [Required]
        public int CustomerId { get; set; }

        [ForeignKey("CustomerId")]
        public virtual Customer Customer { get; set; }

        // BR-ORDER-001: Order amount must be > 0
        [Required]
        [Range(0.01, double.MaxValue, ErrorMessage = "Order amount must be greater than 0")]
        [Column(TypeName = "decimal(18,2)")]
        public decimal TotalAmount { get; set; }

        // BR-ORDER-002: Valid states: Created, Confirmed, Shipped, Delivered, Cancelled
        [Required]
        [StringLength(20)]
        public string Status { get; set; } = OrderStatus.Created;

        [Required]
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

        public DateTime? ConfirmedAt { get; set; }

        public DateTime? ShippedAt { get; set; }

        public DateTime? DeliveredAt { get; set; }

        public DateTime? CancelledAt { get; set; }

        // BR-ORDER-007: Orders must have at least one line item
        [Required]
        [MinLength(1, ErrorMessage = "Order must have at least one line item")]
        public virtual ICollection<OrderLineItem> LineItems { get; set; } = new List<OrderLineItem>();

        // BR-ORDER-008: Shipping address required for physical goods
        [StringLength(500)]
        public string ShippingAddress { get; set; }

        // BR-ORDER-005: Supervisor approval tracking
        public bool RequiresApproval { get; set; } = false;
        public int? ApprovedById { get; set; }
        public DateTime? ApprovedAt { get; set; }

        [StringLength(500)]
        public string Notes { get; set; }
    }

    public class OrderLineItem
    {
        [Key]
        public int Id { get; set; }

        [Required]
        public int OrderId { get; set; }

        [Required]
        [StringLength(100)]
        public string ProductName { get; set; }

        // BR-ORDER-009: Quantity must be >= 1
        [Required]
        [Range(1, int.MaxValue, ErrorMessage = "Quantity must be at least 1")]
        public int Quantity { get; set; }

        // BR-ORDER-010: Unit price must be > 0
        [Required]
        [Range(0.01, double.MaxValue, ErrorMessage = "Unit price must be greater than 0")]
        [Column(TypeName = "decimal(18,2)")]
        public decimal UnitPrice { get; set; }

        public decimal LineTotal => Quantity * UnitPrice;
    }

    public class Customer
    {
        [Key]
        public int Id { get; set; }

        [Required]
        [StringLength(100)]
        public string Name { get; set; }

        [Required]
        [EmailAddress]
        public string Email { get; set; }

        // BR-ORDER-004: VIP customers get 15% discount
        public bool IsVip { get; set; } = false;

        // BR-ORDER-006: Customer credit limit enforcement
        [Column(TypeName = "decimal(18,2)")]
        public decimal CreditLimit { get; set; } = 10000m;

        [Column(TypeName = "decimal(18,2)")]
        public decimal CurrentBalance { get; set; } = 0m;
    }

    public static class OrderStatus
    {
        public const string Created = "Created";
        public const string Confirmed = "Confirmed";
        public const string Shipped = "Shipped";
        public const string Delivered = "Delivered";
        public const string Cancelled = "Cancelled";
    }
}
