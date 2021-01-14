package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.brewery.model.events.AllocateBeerOrderResult;

import java.util.UUID;

public interface BeerOrderManager {

    BeerOrder createOrder(BeerOrder beerOrder);

    // Validation related
    void handleOrderValidation(UUID beerId, boolean isValid);
    // Allocation related
    void handleOrderAllocation(UUID beerId, AllocateBeerOrderResult result);
    // Pickup related
    void beerOrderPickedUp(UUID id);
    //Cancellation
    void cancelOrder(UUID orderId);

}
