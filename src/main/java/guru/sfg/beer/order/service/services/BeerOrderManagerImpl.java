package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.sm.BeerOrderStateChangeInterceptor;
import guru.sfg.brewery.model.BeerOrderDto;
import guru.sfg.brewery.model.events.AllocateBeerOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeerOrderManagerImpl implements BeerOrderManager {

    public static final String BEER_ORDER_ID_HEADER = "beer-order-id";
    private final BeerOrderRepository beerOrderRepository;
    private final StateMachineFactory<BeerOrderStatusEnum, BeerOrderEventEnum> smf;
    private final BeerOrderStateChangeInterceptor interceptorAdapter;

    @Override
    @Transactional
    public BeerOrder createOrder(BeerOrder beerOrder) {
        beerOrder.setId(null);
        beerOrder.setOrderStatus(BeerOrderStatusEnum.NEW);

        BeerOrder saved = beerOrderRepository.saveAndFlush(beerOrder);
        log.info("Saved Beer order into repository with Id " + saved.getId());
        sendBeerOrderEvent(saved, BeerOrderEventEnum.VALIDATE_ORDER);
        return saved;

    }

    @Override
    @Transactional
    public void handleOrderValidation(UUID beerOrderId, boolean isValid) {
        log.debug("Process Validation Result for beerOrderId: " + beerOrderId + " Valid? " + isValid);

        //This was failing sometimes and the fix was to perform an
        //entitymanager.flush()

        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderId);

        beerOrderOptional.ifPresentOrElse(beerOrder -> {
            if(isValid){
                log.info("Validation passed for order {}. Moving to Allocate Order", beerOrderId);
                sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.VALIDATE_PASSED);
                BeerOrder validatedOrder = beerOrderRepository.findById(beerOrderId).get();
                sendBeerOrderEvent(validatedOrder, BeerOrderEventEnum.ALLOCATE_ORDER);
            } else {
                sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.VALIDATE_FAILED);
            }
        }, () -> log.error("handleOrderValidation: Order Not Found. Id: " + beerOrderId));
    }

    @Transactional
    @Override
    public void handleOrderAllocation(UUID beerId, AllocateBeerOrderResult result) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerId);
        beerOrderOptional.ifPresentOrElse(beerOrder -> {
            if (!result.getIsValid()) {
                sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_FAILED);
            } else if (result.getIsValid() && result.getIsPendingInventory()) {
                sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_NO_INVENTORY);
                updateAllocatedQty(result.getBeerOrderDto());
            } else {
                sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_SUCCESS);
                updateAllocatedQty(result.getBeerOrderDto());
            }
        },() -> log.error("handleOrderAllocation: Failed to find the beer by Id " + beerId));

    }

    @Override
    public void beerOrderPickedUp(UUID id) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(id);

        beerOrderOptional.ifPresentOrElse(beerOrder -> {
            //do process
            sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ORDER_PICKED_UP);
        }, () -> log.error("beerOrderPickedUp: Order Not Found. Id: " + id));
    }

    @Override
    public void cancelOrder(UUID orderId) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(orderId);
        beerOrderOptional.ifPresentOrElse(beerOrder -> {
            sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.CANCEL_ORDER);
        },() -> log.error("handleOrderAllocation: Failed to find the beer by Id " + orderId));
    }

    private void updateAllocatedQty(BeerOrderDto beerOrderDto) {
        Optional<BeerOrder> allocatedOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());
        allocatedOrderOptional.ifPresentOrElse( allocatedOrder -> {
            allocatedOrder.getBeerOrderLines().forEach(beerOrderLine -> {
                beerOrderDto.getBeerOrderLines().forEach(beerOrderLineDto -> {
                    if(beerOrderLine.getId() .equals(beerOrderLineDto.getId())){
                        beerOrderLine.setQuantityAllocated(beerOrderLineDto.getQuantityAllocated());
                    }
                });
            });
            beerOrderRepository.saveAndFlush(allocatedOrder);
        },() -> log.error("updateAllocatedQty: Failed to find the beer by Id " + beerOrderDto.getId()));


    }
    private void sendBeerOrderEvent(BeerOrder beerOrder, BeerOrderEventEnum eventEnum) {
        StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm = build(beerOrder);
        Message<BeerOrderEventEnum> msg = MessageBuilder
                .withPayload(eventEnum)
                .setHeader(BEER_ORDER_ID_HEADER, beerOrder.getId())
                .build();
        sm.sendEvent(msg);
    }

    private StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> build(BeerOrder beerOrder) {
        StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm = smf.getStateMachine(beerOrder.getId());

        //reset state
        sm.stop();
        sm.getStateMachineAccessor()
                .doWithAllRegions(context -> {
                    context.addStateMachineInterceptor(interceptorAdapter);
                    context.resetStateMachine(new DefaultStateMachineContext<>(beerOrder.getOrderStatus(), null, null, null));
                });

        //start
        sm.start();
        return sm;
    }
}
