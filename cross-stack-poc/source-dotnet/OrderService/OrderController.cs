using System.Threading.Tasks;
using Microsoft.AspNetCore.Mvc;
using OrderService.Models;
using OrderService.Services;

namespace OrderService.Controllers
{
    /// <summary>
    /// ASP.NET WebAPI controller for Order management.
    /// Exposes REST endpoints: CreateOrder, GetOrder, ConfirmOrder, ShipOrder, DeliverOrder, CancelOrder.
    /// </summary>
    [ApiController]
    [Route("api/[controller]")]
    public class OrderController : ControllerBase
    {
        private readonly Services.OrderService _orderService;
        private readonly ILogger<OrderController> _logger;

        public OrderController(Services.OrderService orderService, ILogger<OrderController> logger)
        {
            _orderService = orderService;
            _logger = logger;
        }

        /// <summary>
        /// Create a new order.
        /// Validates business rules: amount > 0, line items exist, credit limit, VIP discount.
        /// </summary>
        [HttpPost]
        public async Task<IActionResult> CreateOrder([FromBody] CreateOrderRequest request)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            _logger.LogInformation("Creating order for customer {CustomerId}", request.CustomerId);

            var result = await _orderService.CreateOrderAsync(request);

            if (!result.IsSuccess)
            {
                _logger.LogWarning("Order creation failed: {Error}", result.ErrorMessage);
                return BadRequest(new { error = result.ErrorMessage });
            }

            _logger.LogInformation("Order {OrderNumber} created successfully", result.Order.OrderNumber);
            return CreatedAtAction(nameof(GetOrder), new { id = result.Order.Id }, result.Order);
        }

        /// <summary>
        /// Get an order by ID.
        /// </summary>
        [HttpGet("{id}")]
        public async Task<IActionResult> GetOrder(int id)
        {
            var order = await _orderService.GetOrderAsync(id);
            if (order == null)
                return NotFound(new { error = "Order not found" });

            return Ok(order);
        }

        /// <summary>
        /// Confirm an order (Created → Confirmed).
        /// BR-ORDER-002: Only Created orders can be confirmed.
        /// BR-ORDER-005: Orders > 1000 require supervisor approval.
        /// </summary>
        [HttpPost("{id}/confirm")]
        public async Task<IActionResult> ConfirmOrder(int id, [FromBody] ConfirmOrderRequest request)
        {
            _logger.LogInformation("Confirming order {OrderId}", id);

            var result = await _orderService.ConfirmOrderAsync(id, request?.ApprovedById);

            if (!result.IsSuccess)
            {
                _logger.LogWarning("Order confirmation failed: {Error}", result.ErrorMessage);
                return BadRequest(new { error = result.ErrorMessage });
            }

            return Ok(result.Order);
        }

        /// <summary>
        /// Ship an order (Confirmed → Shipped).
        /// BR-ORDER-002: Only Confirmed orders can be shipped.
        /// </summary>
        [HttpPost("{id}/ship")]
        public async Task<IActionResult> ShipOrder(int id)
        {
            _logger.LogInformation("Shipping order {OrderId}", id);

            var result = await _orderService.ShipOrderAsync(id);

            if (!result.IsSuccess)
            {
                _logger.LogWarning("Order shipping failed: {Error}", result.ErrorMessage);
                return BadRequest(new { error = result.ErrorMessage });
            }

            return Ok(result.Order);
        }

        /// <summary>
        /// Deliver an order (Shipped → Delivered).
        /// BR-ORDER-002: Only Shipped orders can be delivered.
        /// </summary>
        [HttpPost("{id}/deliver")]
        public async Task<IActionResult> DeliverOrder(int id)
        {
            _logger.LogInformation("Delivering order {OrderId}", id);

            var result = await _orderService.DeliverOrderAsync(id);

            if (!result.IsSuccess)
            {
                _logger.LogWarning("Order delivery failed: {Error}", result.ErrorMessage);
                return BadRequest(new { error = result.ErrorMessage });
            }

            return Ok(result.Order);
        }

        /// <summary>
        /// Cancel an order.
        /// BR-ORDER-003: Only allowed before shipping (Created or Confirmed status).
        /// </summary>
        [HttpPost("{id}/cancel")]
        public async Task<IActionResult> CancelOrder(int id, [FromBody] CancelOrderRequest request)
        {
            if (string.IsNullOrWhiteSpace(request?.Reason))
                return BadRequest(new { error = "Cancellation reason is required" });

            _logger.LogInformation("Cancelling order {OrderId}: {Reason}", id, request.Reason);

            var result = await _orderService.CancelOrderAsync(id, request.Reason);

            if (!result.IsSuccess)
            {
                _logger.LogWarning("Order cancellation failed: {Error}", result.ErrorMessage);
                return BadRequest(new { error = result.ErrorMessage });
            }

            return Ok(result.Order);
        }
    }

    // --- Request DTOs ---

    public class ConfirmOrderRequest
    {
        public int? ApprovedById { get; set; }
    }

    public class CancelOrderRequest
    {
        public string Reason { get; set; }
    }
}
