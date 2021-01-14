package guru.sfg.beer.order.service.testcomponents;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.events.AllocateBeerOrderRequest;
import guru.sfg.brewery.model.events.AllocateBeerOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Created by jt on 2/16/20.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderAllocationListener {

    private final JmsTemplate jmsTemplate;

    @JmsListener(destination = JmsConfig.ALLOCATE_ORDER_QUEUE)
    public void listen(Message msg){
        AllocateBeerOrderRequest request = (AllocateBeerOrderRequest) msg.getPayload();

        request.getBeerOrderDto().getBeerOrderLines().forEach(beerOrderLineDto -> {
            beerOrderLineDto.setQuantityAllocated(beerOrderLineDto.getOrderQuantity());
        });

        boolean isValid = true, isPendingInventory = false, sendResponse = true;
        if (null != request.getBeerOrderDto().getCustomerRef()) {
            String param = request.getBeerOrderDto().getCustomerRef();
            switch (param) {
                case "inventory-pending":
                    isValid = true;
                    isPendingInventory = true;
                    break;
                case "fail-allocation":
                    isValid = false;
                    break;
                case "dont-allocate":
                    sendResponse = false;
                    break;
            }
        }

        if (sendResponse) {
            jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_RESPONSE_QUEUE,
                    AllocateBeerOrderResult.builder()
                            .beerOrderDto(request.getBeerOrderDto())
                            .isPendingInventory(isPendingInventory)
                            .isValid(isValid)
                            .build());
        }

    }
}