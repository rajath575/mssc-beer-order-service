package guru.sfg.beer.order.service.testcomponents;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.events.ValidateBeerOrderResult;
import guru.sfg.brewery.model.events.ValidateBeerOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderValidationListener {
    private final JmsTemplate jmsTemplate;

    @JmsListener(destination = JmsConfig.VALIDATE_ORDER_QUEUE)
    public void listen(Message msg) {

        ValidateBeerOrderRequest request = (ValidateBeerOrderRequest) msg.getPayload();
        boolean isValid = true, sendResponse = true;
        if (null != request.getBeerOrderDto().getCustomerRef()
                && request.getBeerOrderDto().getCustomerRef().equals("fail-validation")) {
            isValid = false;
        } else if (null != request.getBeerOrderDto().getCustomerRef()
                && request.getBeerOrderDto().getCustomerRef().equals("dont-validate")) {
            sendResponse = false;
        }

        log.info("Returning mock result for orderId: " + request.getBeerOrderDto().getId());
        if (sendResponse) {
            jmsTemplate.convertAndSend(JmsConfig.VALIDATE_ORDER_RESPONSE_QUEUE,
                    ValidateBeerOrderResult.builder()
                            .isValid(isValid)
                            .orderId(request.getBeerOrderDto().getId())
                            .build());
        }
    }
}