package guru.sfg.beer.order.service.sm.actions;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.services.BeerOrderManagerImpl;
import guru.sfg.beer.order.service.web.mappers.BeerOrderMapper;
import guru.sfg.brewery.model.events.ValidateBeerOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class BeerOrderValidationAction implements Action<BeerOrderStatusEnum, BeerOrderEventEnum> {

    private final JmsTemplate jmsTemplate;
    private final BeerOrderRepository repository;
    private final BeerOrderMapper mapper;

    @Transactional
    @Override
    public void execute(StateContext<BeerOrderStatusEnum, BeerOrderEventEnum> stateContext) {
        log.info("Validate Order action called");
        UUID beerOrderId = (UUID) stateContext.getMessage().getHeaders().get(BeerOrderManagerImpl.BEER_ORDER_ID_HEADER);
        Optional<BeerOrder> orderOptional = repository.findById(beerOrderId);
        orderOptional.ifPresentOrElse(beerOrder -> {
            ValidateBeerOrderRequest request = ValidateBeerOrderRequest.builder()
                    .beerOrderDto(mapper.beerOrderToDto(beerOrder))
                    .build();
            jmsTemplate.convertAndSend(JmsConfig.VALIDATE_ORDER_QUEUE, request);
        },() ->log.error("Failed to find beer order by Id " + beerOrderId));

    }
}
